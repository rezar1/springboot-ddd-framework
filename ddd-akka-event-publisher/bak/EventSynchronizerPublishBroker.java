package com.zero.ddd.akka.event.publisher2.actor.broker;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.zero.ddd.akka.cluster.core.helper.ClientAskRemoteExecutorConfig;
import com.zero.ddd.akka.cluster.core.initializer.serializer.SelfProtoBufObject;
import com.zero.ddd.akka.cluster.toolset.distributedJob.actor.JobWorker.JobWorkerCommand;
import com.zero.ddd.akka.cluster.toolset.distributedJob.actor.JobWorker.StartJobTaskCommand;
import com.zero.ddd.akka.event.publisher2.actor.ServiceKeyHolder;
import com.zero.ddd.akka.event.publisher2.actor.consumer.EventSynchConsuemr.EventSynchConsuemrEvent;
import com.zero.ddd.akka.event.publisher2.domain.partitionEvent.PartitionEventStore;
import com.zero.ddd.akka.event.publisher2.domain.synchronizerState.PartitionAssignState;
import com.zero.ddd.akka.event.publisher2.domain.synchronizerState.SynchronizerState;
import com.zero.ddd.akka.event.publisher2.domain.synchronizerState.SynchronizerStateRepo;
import com.zero.ddd.akka.event.publisher2.event.EventSynchronizer;
import com.zero.ddd.akka.event.publisher2.event.IRecordLastOffsetId;
import com.zero.ddd.akka.event.publisher2.publisher.EventPublisherFactory;
import com.zero.helper.GU;
import com.zero.helper.JacksonUtil;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.delivery.ConsumerController;
import akka.actor.typed.delivery.ConsumerController.Confirmed;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import akka.actor.typed.receptionist.Receptionist;
import akka.actor.typed.receptionist.Receptionist.Listing;
import akka.actor.typed.receptionist.ServiceKey;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2023-06-12 03:58:36
 * @Desc 些年若许,不负芳华.
 *
 */
@Slf4j
public class EventSynchronizerPublishBroker {
	
	public static Behavior<EventSynchronizerBrokerEvent> create(
			String eventSynchronizerId,
			SynchronizerStateRepo db,
			ActorRef<ConsumerController.Start<EventSynchronizerBrokerEvent>> consumerController) {
		return Init.create(eventSynchronizerId, db, consumerController);
	}
	
	static class Init extends AbstractBehavior<EventSynchronizerBrokerEvent> {

		private final String eventSynchronizerId;
		private final SynchronizerStateRepo db;
		private final ActorRef<ConsumerController.Start<EventSynchronizerBrokerEvent>> consumerController;

		private Init(
				String eventSynchronizerId, 
				SynchronizerStateRepo db,
				ActorContext<EventSynchronizerBrokerEvent> context, 
				ActorRef<ConsumerController.Start<EventSynchronizerBrokerEvent>> consumerController) {
			super(context);
			this.eventSynchronizerId = eventSynchronizerId;
			this.db = db;
			this.consumerController = consumerController;
		}

		static Behavior<EventSynchronizerBrokerEvent> create(
				String eventSynchronizerId,
				SynchronizerStateRepo db,
				ActorRef<ConsumerController.Start<EventSynchronizerBrokerEvent>> consumerController) {
			return Behaviors.setup(context -> {
				context.pipeToSelf(
						db.loadSynchronizerState(eventSynchronizerId),
						(state, exc) -> {
							log.info("事件同步器:[{}], 分布式缓存中的数据:{}", eventSynchronizerId, state);
							if (exc == null)
								return new InitialState(state);
							else
								return new InnerDBError(exc);
						});
				return new Init(
						eventSynchronizerId,
						db,
						context, 
						consumerController);
			});
		}

		@Override
		public Receive<EventSynchronizerBrokerEvent> createReceive() {
			return newReceiveBuilder()
					.onMessage(InitialState.class, this::onInitialState)
					.onMessage(InnerDBError.class, EventSynchronizerPublishBroker::onDBError)
					.build();
		}

		private Behavior<EventSynchronizerBrokerEvent> onInitialState(
				InitialState initial) {
			ActorRef<ConsumerController.Delivery<EventSynchronizerBrokerEvent>> deliveryAdapter = 
					getContext().messageAdapter(
							ConsumerController.deliveryClass(), 
							d -> new CommandDelivery(
									d.message(), 
									d.confirmTo()));
			consumerController.tell(
					new ConsumerController.Start<>(
							deliveryAdapter));
			return Active.create(eventSynchronizerId, db, initial.state);
		}
	}
	
	static class Active {
		
		private static final ActorRef<Confirmed> NO_CONFIRM = null;

		// EventSynchronizerBrokerEvent
		public static Behavior<EventSynchronizerBrokerEvent> create(
				String eventSynchronizerId, 
				SynchronizerStateRepo db,
				SynchronizerState state) {
			return Behaviors.setup(
					context -> 
						new Active(
								eventSynchronizerId,
								state == null ? new SynchronizerState() : state, 
								db, 
								context)
						.startEventPublish());
		}
		
		private final String eventSynchronizerId;
		private final SynchronizerStateRepo stateRepo;
		private final ActorContext<EventSynchronizerBrokerEvent> context;
		private final ServiceKey<EventSynchConsuemrEvent> eventConsumerServiceKey;
		private final Map<String, ActorRef<EventSynchConsuemrEvent>> onlineWorker = new HashMap<>();
		
		private SynchronizerState state;
		
		public Active(
				String eventSynchronizerId,
				SynchronizerState state,
				SynchronizerStateRepo stateRepo,
				ActorContext<EventSynchronizerBrokerEvent> context) {
			this.eventSynchronizerId = eventSynchronizerId;
			this.state = state;
			this.context = context;
			this.stateRepo = stateRepo;
			this.eventConsumerServiceKey = 
					ServiceKeyHolder.eventConsumerServiceKey(
							this.eventSynchronizerId);
			this.subscribeEventConsumerService();
			this.registeAsSchedulerService();
			log.info(
					"事件主题:[{}] 启动成功!!", 
					this.eventSynchronizerId);
		}
		
		private Behavior<EventSynchronizerBrokerEvent> startEventPublish() {
			return Behaviors.receive(EventSynchronizerBrokerEvent.class)
					.onMessage(CommandDelivery.class, this::onDelivery)
					.onMessage(InnerDBError.class, EventSynchronizerPublishBroker::onDBError)
					.onMessage(EventConsumerListingResponse.class, this::updateOnlineEventConsumerList)
					.build();
		}
		
		private Behavior<EventSynchronizerBrokerEvent> submitRootTask() {
			if (this.state.duringRunning()) {
				// 任务还在运行中，等待任务结束后判断是否继续执行
				log.info(
						"作业:[{}] 当前仍在运行中 开始于:[{}] 已执行:[{}ms]", 
						this.job.jobShowName(), 
						this.state.formatStartedAt(),
						Duration.between(
								this.state.startedAt(),
								LocalDateTime.now())
						.toMillis());
			} else {
				// 开始执行调度
				// 根据不同的任务类型进行初始调度
				this.state.startRootTask(
						this.job.getJobParams(),
						this.job.getJobExecuteType(), 
						this.onlineWorker.size());
				this.dispatchTask();
			}
			this.startJobScheduleTimeout(
					this.state.getJobCircle(),
					this.state.getStartedAt());
			return Behaviors.same();
		}
		
		// TODO 在超时执行后，计算的startedAt时间不应该是失效的那个时间
		private void startJobScheduleTimeout(
				int jobCircle,
				LocalDateTime startedAt) {
			LocalDateTime timeoutAt = 
					this.job.jobScheduleTimeout(
							startedAt);
			if (timeoutAt != null) {
				JobScheduleTimeout jobScheduleTimeout = 
						new JobScheduleTimeout(
								jobCircle,
								timeoutAt);
				// 任务调度已超时
				if (timeoutAt.isBefore(LocalDateTime.now())) {
					this.context.getSelf().tell(jobScheduleTimeout);
				} else {
					if (this.scheduleTimeoutCancellable != null
							&& !this.scheduleTimeoutCancellable.isCancelled()) {
						this.scheduleTimeoutCancellable.cancel();
					}
					log.info("作业:[{}] 将在:[{}]时间点调度超时，届时将发送异常通知", this.job.jobShowName(), timeoutAt);
					this.scheduleTimeoutCancellable = 
							this.context.scheduleOnce(
									Duration.between(
											LocalDateTime.now(), 
											timeoutAt),
									this.context.getSelf(),
									jobScheduleTimeout);
					}
				}
		}

		// 执行任务调度的定时器
		private void startNextScheduleTrigger() {
			try {
				LocalDateTime nextExtExecutionTime = 
						this.job.nextExtExecutionTime(
								this.jobCalculateTime());
				// 如果根据上一次任务的时间计算的下次调度时间已经过期，直接开始一次调度
				this.currentScheduleAt = 
						nextExtExecutionTime.isBefore(LocalDateTime.now()) ? 
								LocalDateTime.now() : nextExtExecutionTime;
				log.info("作业:[{}] 将在:[{}]时间点执行初始调度", this.job.jobShowName(), currentScheduleAt);
				this.context.scheduleOnce(
						 Duration.between(
									LocalDateTime.now(), 
									currentScheduleAt),
						this.context.getSelf(),
						ScheduleJobOperation.START_JOB_MASTER_ROOT_REQUEST);
			} catch (Exception e) {
				log.error("Error:{}", e);
			}
		}
		
		private LocalDateTime jobCalculateTime() {
			// 根据任务类型，获取用于计算任务下次执行时间的起始时间
			return 
					this.state.jobCalculateTime(
							this.job.scheduledTimeType());
		}

		// 运行时工作节点列表发生变化 
		// 1. 更新所有归属到下线节点上的任务进行重新分配
		private Behavior<EventSynchronizerBrokerEvent> updateOnlineEventConsumerList(
				EventConsumerListingResponse changed) {
			Set<ActorRef<EventSynchConsuemrEvent>> services = 
					changed.getServices(eventConsumerServiceKey);
			Map<String, ActorRef<EventSynchConsuemrEvent>> currentOnlieWorker = 
					services.stream()
					.collect(
							Collectors.toMap(
									ref -> ref.path().name(), 
									ref -> ref));
			log.info(
					"事件主题:[{}] 刷新当前在线消费节点:{}", 
					this.eventSynchronizerId, 
					currentOnlieWorker.keySet());
			Set<String> onlineEventConsumer = 
					currentOnlieWorker.keySet();
			this.onlineWorker.clear();
			this.onlineWorker.putAll(currentOnlieWorker);
			if (this.state.hasSyncConfig()) {
				// 如果存在配置，看下如何分配任务
				this.state.refreshOnlineEventConsumer(
						onlineEventConsumer);
				this.dispatchTask();
			}
			return Behaviors.same();
		}

		private void dispatchTask() {
			this.dispatchTask(NO_CONFIRM);
		}

		/**
		 * 
		 * @param confirmTo
		 */
		private void dispatchTask(
				ActorRef<Confirmed> confirmTo) {
			List<PartitionAssignState> waitDispatchTask = 
					this.state.waitDispatchPartition(
							this.onlineWorker.keySet());
			try {
				this.state.incrVersion();
				this.stateRepo.updateSynchronizerState(
						this.eventSynchronizerId, 
						this.state.clone())
				.whenCompleteAsync(
						(done, error) -> {
							if (done != null) {
								// 先进行存储，再进行通知, 如果存储异常, 任务也不会重复分发；存储成功，tell失败，重启后先进行Assigned的任务进行重新tell
								this.assignTaskToWorker(waitDispatchTask);
								this.confirmToWorker(confirmTo);
							} else {
								log.error("事件主题:[" + this.eventSynchronizerId + "] 状态更新异常", error);
								throw new IllegalStateException(
										"事件主题:[" + this.eventSynchronizerId + "] 状态更新异常",
										error);
							}
						}, 
						ClientAskRemoteExecutorConfig.askRemoteExecutor())
				.toCompletableFuture()
				.get();
			} catch (Exception e) {
				log.error("事件主题:[" + this.eventSynchronizerId + "] 状态更新异常", e);
				throw new IllegalStateException(e);
			}
		}
		
		private void confirmToWorker(
				ActorRef<Confirmed> confirmTo) {
			if (confirmTo != null) {
				confirmTo.tell(
						ConsumerController.confirmed());
			}
		}

		/**
		 * 如果因为重启导致的，任务
		 */
		private void dispatchAssignedButNotRunningTask() {
			this.assignTaskToWorker(
					this.state.assignedButNotRunningTask());
		}

		private void assignTaskToWorker(
				List<PartitionAssignState> waitDispatchTask) {
			if (GU.notNullAndEmpty(waitDispatchTask)) {
				waitDispatchTask.forEach(
						task -> {
							// 这里开始连接分区发布者和消费者 TODO
							ActorRef<EventSynchConsuemrEvent> actorRef = 
									this.onlineWorker.get(
											task.getAssignedTo());
							if (actorRef != null) {
								actorRef
								.tell(
										new StartJobTaskCommand(
												task.getTaskId(),
												task.getTaskType(),
												task.getTaskBody(),
												task.getStartedAt()));
							}
						});
			}
		}

		private Behavior<EventSynchronizerBrokerEvent> onDelivery(
				CommandDelivery delivery) {
			log.info("事件主题:[{}] 获取到上报事件:{}", this.eventSynchronizerId, delivery.command);
			if (delivery.command instanceof EventSynchronizerInfoPing) {
				EventSynchronizerInfoPing ping = (EventSynchronizerInfoPing) delivery.command;
				// 客户端上报当前配置的事件
				if (this.state.tryRefresh(
						ping.getEventSynchronizer())) {
					// 调整分片，关注事件类型等操作
					this.refreshPublisher();
				}
				delivery.confirmTo.tell(
						ConsumerController.confirmed());
				return Behaviors.same();
			}
			if (delivery.command instanceof CompleteJobTask) {
				CompleteJobTask completeTask = 
						(CompleteJobTask) delivery.command;
				this.state.completeJobTask(
						completeTask.getTaskId(), 
						completeTask.getTaskResult(), 
						completeTask.success);
				this.checkJobIsCompleted();
			} else if (delivery.command instanceof MapTask) {
				MapTask mapTask = 
						(MapTask) delivery.command;
				this.state.mapJobTask(
						mapTask.getTaskId(), 
						mapTask.getTaskType(),
						mapTask.getMapedTasks());
			} else if (delivery.command instanceof JobTaskStartedRunning) {
				JobTaskStartedRunning task = 
						(JobTaskStartedRunning) delivery.command;
				this.state.jobTaskStartRunning(task.getTaskId());
			} else {
				return Behaviors.unhandled();
			}
			this.dispatchTask(
					delivery.confirmTo);
			return Behaviors.same();
		}
		
		private void refreshPublisher() {
			// TODO Auto-generated method stub
			
		}

		private void registeAsSchedulerService() {
			this.context
			.getSystem()
			.receptionist()
			.tell(
					Receptionist.register(
							ServiceKeyHolder.eventPublisherServiceKey(
									this.eventSynchronizerId),
							this.context.getSelf()));
		}

		/**
		 * 订阅当前job所有执行节点服务列表的变更
		 */
		private void subscribeEventConsumerService() {
			ActorRef<Listing> listingResponseAdapter = 
					this.context
					.messageAdapter(
							Receptionist.Listing.class, 
							EventConsumerListingResponse::new);
			this.context
			.getSystem()
			.receptionist()
			.tell(
					Receptionist.subscribe(
							eventConsumerServiceKey, 
							listingResponseAdapter));
		}
		
		// 查看job是否完成了当前轮次的调度
		private void checkJobIsCompleted() {
			if (this.state.isCompleted()) {
				// 当前轮次的任务已完成
				this.logCurrentJobCircleState();
				this.afterJobCompleted();
				// 结束当前调度
				this.state.completedTask();
				// 应该立即开启新的一轮调用，否则等待定时器触发
				if (this.currentScheduleAt.isBefore(LocalDateTime.now())) {
					this.startNextScheduleTrigger();
				}
			}
		}

		// 任务执行完成后执行的操作
		private void afterJobCompleted() {
			if (this.scheduleTimeoutCancellable != null) {
				this.scheduleTimeoutCancellable.cancel();
				this.scheduleTimeoutCancellable = null;
			}
		}

		@SuppressWarnings("unused")
		private Behavior<JobScheduledCommand> onSaveSuccess(
				SaveSuccessAndConfirm success) {
			if (success.confirmTo != null) {
				success.confirmTo.tell(
						ConsumerController.confirmed());
			}
			return Behaviors.same();
		}

		private void logCurrentJobCircleState() {
			log.info(
					"作业:[{}] 调度轮次:[{}] 调度结束，轮次调度情况:{}", 
					this.job.jobShowName(),
					this.state.getJobCircle(), 
					JacksonUtil.obj2Str(this.state));
		}

	}
	
	private static Behavior<EventSynchronizerBrokerEvent> onDBError(
			InnerDBError error) throws Exception {
		throw error.cause;
	}
	
	
	public EventSynchronizerPublishBroker(
			String eventSynchronizerId,
			IRecordLastOffsetId iRecordLastOffsetId,
			PartitionEventStore partitionEventStore,
			EventPublisherFactory eventPublisherFactory,
			ActorContext<EventSynchronizerBrokerEvent> context) {
	}

	public static interface EventSynchronizerBrokerEvent extends SelfProtoBufObject {
	}

	@Data
	@ToString
	@NoArgsConstructor
	@AllArgsConstructor
	private static class EventSynchronizerInfoPing implements EventSynchronizerBrokerEvent {
		private EventSynchronizer eventSynchronizer;
	}
	
	private static class InitialState implements EventSynchronizerBrokerEvent {
		final SynchronizerState state;
		private InitialState(
				SynchronizerState state) {
			this.state = state;
		}
	}
	
	private static class InnerDBError implements EventSynchronizerBrokerEvent {
		final Exception cause;

		private InnerDBError(Throwable cause) {
			if (cause instanceof Exception)
				this.cause = (Exception) cause;
			else
				this.cause = new RuntimeException(cause.getMessage(), cause);
		}
	}
	
	private static class CommandDelivery implements EventSynchronizerBrokerEvent {
		
		final EventSynchronizerBrokerEvent command;
		final ActorRef<ConsumerController.Confirmed> confirmTo;

		private CommandDelivery(
				EventSynchronizerBrokerEvent command, 
				ActorRef<ConsumerController.Confirmed> confirmTo) {
			this.command = command;
			this.confirmTo = confirmTo;
		}
	}
	
	@ToString
	@RequiredArgsConstructor
	public static class EventConsumerListingResponse implements EventSynchronizerBrokerEvent {
		
		final Receptionist.Listing listing;
		
		public Set<ActorRef<EventSynchConsuemrEvent>> getServices(
				ServiceKey<EventSynchConsuemrEvent> serviceKey) {
			return listing.getServiceInstances(serviceKey);
		}
		
	}
	
}