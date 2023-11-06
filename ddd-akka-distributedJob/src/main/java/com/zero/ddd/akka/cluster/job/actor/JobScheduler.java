package com.zero.ddd.akka.cluster.job.actor;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.zero.ddd.akka.cluster.core.helper.ClientAskRemoteExecutorConfig;
import com.zero.ddd.akka.cluster.core.initializer.serializer.SelfProtoBufObject;
import com.zero.ddd.akka.cluster.job.actor.JobWorker.JobWorkerCommand;
import com.zero.ddd.akka.cluster.job.actor.JobWorker.StartJobTaskCommand;
import com.zero.ddd.akka.cluster.job.definition.JobEndpoint;
import com.zero.ddd.akka.cluster.job.model.JobDatabase;
import com.zero.ddd.akka.cluster.job.model.JobInstanceState;
import com.zero.ddd.akka.cluster.job.model.JobTask;
import com.zero.helper.GU;
import com.zero.helper.JacksonUtil;

import akka.actor.Cancellable;
import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.SupervisorStrategy;
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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2023-04-18 02:29:04
 * @Desc 些年若许,不负芳华.
 * 
 * 处理job的调度
 * 调度节点每次状态变更都需要刷新到ddata中，ddata以appname为同步维度
 * 执行节点掉线后，是否重新分发任务?
 * 关键问题在于重新分片之后，已执行的任务如何恢复
 * 	1. 启动之后，由缓存中的状态，向执行节点发起查询上报
 * 基于集群分片
 * 
 */
@Slf4j(topic = "job")
public class JobScheduler {
	
	public static Behavior<JobScheduledCommand> create(
			String jobName,
			Function<String, JobEndpoint> suppiler,
			JobDatabase db,
			ActorRef<ConsumerController.Start<JobScheduledCommand>> consumerController) {
		return Init.create(suppiler.apply(jobName), db, consumerController);
	}
	
	public static Behavior<JobScheduledCommand> create(
			JobEndpoint job, 
			JobDatabase db,
			ActorRef<ConsumerController.Start<JobScheduledCommand>> consumerController) {
		return Init.create(job, db, consumerController);
	}
	
	static class Init extends AbstractBehavior<JobScheduledCommand> {

		private final JobEndpoint job;
		private final JobDatabase db;
		private final ActorRef<ConsumerController.Start<JobScheduledCommand>> consumerController;

		private Init(
				JobEndpoint job, 
				JobDatabase db,
				ActorContext<JobScheduledCommand> context, 
				ActorRef<ConsumerController.Start<JobScheduledCommand>> consumerController) {
			super(context);
			this.job = job;
			this.db = db;
			this.consumerController = consumerController;
		}

		static Behavior<JobScheduledCommand> create(
				JobEndpoint job, 
				JobDatabase db,
				ActorRef<ConsumerController.Start<JobScheduledCommand>> consumerController) {
			return Behaviors.setup(context -> {
				context.pipeToSelf(
						db.loadJobInstance(job.getJobName()),
						(state, exc) -> {
							log.info("作业:[{}], 分布式缓存中的数据:{}", job.jobShowName(), state);
							if (exc == null)
								return new InitialState(state);
							else
								return new InnerDBError(exc);
						});
				return new Init(
						job,
						db,
						context, 
						consumerController);
			});
		}

		@Override
		public Receive<JobScheduledCommand> createReceive() {
			return newReceiveBuilder()
					.onMessage(InitialState.class, this::onInitialState)
					.onMessage(InnerDBError.class, JobScheduler::onDBError)
					.build();
		}

		private Behavior<JobScheduledCommand> onInitialState(
				InitialState initial) {
			ActorRef<ConsumerController.Delivery<JobScheduledCommand>> deliveryAdapter = 
					getContext().messageAdapter(
							ConsumerController.deliveryClass(), 
							d -> new CommandDelivery(
									d.message(), 
									d.confirmTo()));
			consumerController.tell(
					new ConsumerController.Start<>(
							deliveryAdapter));
			return Active.create(job, db, initial.state);
		}
	}
	
	
	static class Active {
		
		private static final ActorRef<Confirmed> NO_CONFIRM = null;

		public static Behavior<JobScheduledCommand> create(
				JobEndpoint job, 
				JobDatabase db,
				JobInstanceState state) {
			return 
					Behaviors.supervise(
							Behaviors.<JobScheduledCommand>setup(
									context -> 
										new Active(
												job, 
												state == null ? job.initJobInstance() : state,
												db, 
												context).jobScheduleing()))
					.onFailure(
							Exception.class, 
							SupervisorStrategy.restart());
		}
		
		private final JobEndpoint job;
		private final JobDatabase jobDatabase;
		private final ActorContext<JobScheduledCommand> context;
		private final ServiceKey<JobWorkerCommand> jobServiceKey;
		private final Map<String, ActorRef<JobWorkerCommand>> onlineWorker = new HashMap<>();
		
		private JobInstanceState state;
		private LocalDateTime currentScheduleAt;
		private Cancellable scheduleTimeoutCancellable;
		private boolean hasDispatchAssignedButNotRunningTask;
		
		public Active(
				JobEndpoint job,
				JobInstanceState state,
				JobDatabase jobDatabase,
				ActorContext<JobScheduledCommand> context) {
			this.job = job;
			this.state = state;
			this.context = context;
			this.jobDatabase = jobDatabase;
			this.jobServiceKey = 
					ServiceKeyHolder.jobWorkerServiceKey(
							this.job.getJobName());
			this.subscribeWorkerService();
			this.registeAsSchedulerService();
			log.info(
					"作业:[{}] 调度器启动成功!!", 
					this.job.jobShowName());
		}
		
		private void registeAsSchedulerService() {
			this.context
			.getSystem()
			.receptionist()
			.tell(
					Receptionist.register(
							ServiceKeyHolder.jobSchedulerServiceKey(
									this.job.getJobName()),
							this.context.getSelf()));
		}

		/**
		 * 订阅当前job所有执行节点服务列表的变更
		 */
		private void subscribeWorkerService() {
			ActorRef<Listing> listingResponseAdapter = 
					this.context
					.messageAdapter(
							Receptionist.Listing.class, 
							TaskWorkerListingResponse::new);
			this.context
			.getSystem()
			.receptionist()
			.tell(
					Receptionist.subscribe(
							jobServiceKey, 
							listingResponseAdapter));
		}
		
		private Behavior<JobScheduledCommand> jobScheduleing() {
			this.state.init(
					this.job.getJobName());
			this.startNextScheduleTrigger();
			return Behaviors.receive(JobScheduledCommand.class)
					.onMessage(CommandDelivery.class, this::onDelivery)
					.onMessage(InnerDBError.class, JobScheduler::onDBError)
					.onMessage(TaskWorkerListingResponse.class, this::updateOnlineWorkerList)
					.onMessage(JobScheduleTimeout.class, this::scheduleTimeout)
					.onMessageEquals(ScheduleJobOperation.START_JOB_MASTER_ROOT_REQUEST, this::submitRootTask)
					.build();
		}
		
		private Behavior<JobScheduledCommand> scheduleTimeout(
				JobScheduleTimeout timeout) {
			if (timeout.getJobCircle() == this.state.getJobCircle()
					&& this.state.duringRunning()) {
				this.handleJobScheduleTimeout();
			}
			return Behaviors.same();
		}
		
		private void handleJobScheduleTimeout() {
			this.job.jobScheduleTimeoutListener()
			.ifPresent(listener -> {
				listener.onJobScheudleTimeout(
						this.job.jobShowName(), 
						this.state.startedAt(), 
						this.onlineWorker.keySet(),
						this.state.snapshotAssignedTask());
			});
		}

		private Behavior<JobScheduledCommand> submitRootTask() {
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
		private Behavior<JobScheduledCommand> updateOnlineWorkerList(
				TaskWorkerListingResponse changed) {
			Set<ActorRef<JobWorkerCommand>> services = 
					changed.getServices(jobServiceKey);
			Map<String, ActorRef<JobWorkerCommand>> currentOnlieWorker = 
					services.stream()
					.collect(
							Collectors.toMap(
									ref -> ref.path().name(), 
									ref -> ref));
			log.info("作业:[{}] 刷新当前在线工作节点:{}",this.job.jobShowName(), currentOnlieWorker.keySet());
			Set<String> workerServers = currentOnlieWorker.keySet();
			this.onlineWorker.clear();
			this.onlineWorker.putAll(currentOnlieWorker);
			if (!hasDispatchAssignedButNotRunningTask) {
				this.dispatchAssignedButNotRunningTask();
				this.hasDispatchAssignedButNotRunningTask = true;
			}
			try {
				if (this.state.duringRunning()) {
					if (this.state.isDuringWaitStartRootTask()) {
						this.state.startRootTask(
								this.job.getJobParams(),
								this.job.getJobExecuteType(), 
								this.onlineWorker.size());
					} else {
						this.state.refreshOnlineWorker(
								workerServers);
					}
					this.dispatchTask();
				}
			} catch (Exception e) {
				log.error("error:{}", e);
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
			List<JobTask> waitDispatchTask = 
					this.state.waitDispatchTask(
							this.onlineWorker.keySet());
			try {
				this.state.incrVersion();
				this.jobDatabase.updateJobInstance(
						this.job.getJobName(), 
						this.state.clone())
				.whenCompleteAsync(
						(done, error) -> {
							if (done != null) {
								// 先进行存储，再进行通知, 如果存储异常, 任务也不会重复分发；存储成功，tell失败，重启后先进行Assigned的任务进行重新tell
								this.assignTaskToWorker(waitDispatchTask);
								this.confirmToWorker(confirmTo);
							} else {
								// TODO 重置状态
								log.error("作业:[" + this.job.jobShowName() + "] 状态更新异常", error);
								throw new IllegalStateException(
										"作业:[" + this.job.jobShowName() + "] 状态更新异常",
										error);
							}
						}, 
						ClientAskRemoteExecutorConfig.askRemoteExecutor())
				.toCompletableFuture()
				.get();
			} catch (Exception e) {
				log.error("作业:[" + this.job.jobShowName() + "] 状态更新异常", e);
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
				List<JobTask> waitDispatchTask) {
			if (GU.notNullAndEmpty(waitDispatchTask)) {
				waitDispatchTask.forEach(
						task -> {
							ActorRef<JobWorkerCommand> actorRef = 
									this.onlineWorker.get(
											task.getWorker());
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

		private Behavior<JobScheduledCommand> onDelivery(
				CommandDelivery delivery) {
			// 两种命令 1: CompleteTask 2: mapTask 3. updateRunningStatus
			// 有咩有可能是存储失败，但分发成功，客户端成功执行了 ?
			// 看下是不是判断下circle, 如果一致的话说明是同轮次的task
			if (isStartJobScheduleCommand(delivery.command)
					|| (delivery.command instanceof WithJobTaskId
							&& !this.state.containJobTask(
									((WithJobTaskId)delivery.command).getTaskId()))) {
				log.info("作业:[{}] 仅仅执行Confirm的事件:{}", this.job.jobShowName(), delivery.command);
				delivery.confirmTo.tell(
						ConsumerController.confirmed());
				return Behaviors.same();
			}
			log.info("作业:[{}] 获取到任务生命周期上报事件:{}", this.job.jobShowName(), delivery.command);
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
					"作业:[{}] 调度轮次:[{}] 预期开始时间:[{}] 调度结束，轮次调度情况:{}", 
					this.job.jobShowName(),
					this.state.getJobCircle(),
					this.currentScheduleAt,
					JacksonUtil.obj2Str(this.state));
		}

	}
	
	public static interface JobScheduledCommand extends SelfProtoBufObject {
		
	}
	
	public static interface WithJobTaskId {
		public String getTaskId();
	}
	
	@Data
	@ToString
	@NoArgsConstructor
	@AllArgsConstructor
	public static class JobTaskStartedRunning implements JobScheduledCommand, WithJobTaskId {
		private String taskId;
	}
	
	@Data
	@ToString
	@NoArgsConstructor
	@AllArgsConstructor
	public static class CompleteJobTask implements JobScheduledCommand, WithJobTaskId {
		private String taskId;
		private String taskResult;
		private boolean success;
	}
	
	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class MapTask implements JobScheduledCommand, WithJobTaskId {
		
		private String taskId;
		private String taskType;
		@JsonIgnore
	    private List<byte[]> mapedTasks;
		@Override
		public String toString() {
			return "MapTask [taskId=" + taskId + ", taskType=" + taskType + ", mapedTasks=" + mapedTasks.size() + "]";
		}
	}

	private static class InitialState implements JobScheduledCommand {
		final JobInstanceState state;
		private InitialState(
				JobInstanceState state) {
			this.state = state;
		}
	}

	private static class SaveSuccessAndConfirm implements JobScheduledCommand {
		final ActorRef<ConsumerController.Confirmed> confirmTo;
		
		private SaveSuccessAndConfirm(
				ActorRef<ConsumerController.Confirmed> confirmTo) {
			this.confirmTo = confirmTo;
		}
	}

	private static class InnerDBError implements JobScheduledCommand {
		final Exception cause;

		private InnerDBError(Throwable cause) {
			if (cause instanceof Exception)
				this.cause = (Exception) cause;
			else
				this.cause = new RuntimeException(cause.getMessage(), cause);
		}
	}

	private static class CommandDelivery implements JobScheduledCommand {
		
		final JobScheduledCommand command;
		final ActorRef<ConsumerController.Confirmed> confirmTo;

		private CommandDelivery(
				JobScheduledCommand command, 
				ActorRef<ConsumerController.Confirmed> confirmTo) {
			this.command = command;
			this.confirmTo = confirmTo;
		}
	}
	
	private static Behavior<JobScheduledCommand> onDBError(
			InnerDBError error) throws Exception {
		throw error.cause;
	}
	
	public static boolean isStartJobScheduleCommand(
			JobScheduledCommand command) {
		boolean res = false;
		if (command instanceof ScheduleJobOperation) {
			// 反序列化可能存在类加载器不一致的情况
			ScheduleJobOperation opt = (ScheduleJobOperation) command;
			res = opt.ordinal() == ScheduleJobOperation.START_JOB_SCHEDULER.ordinal();
		}
		return res;
	}

	public static enum JobMasterCompletedTaskState implements JobScheduledCommand {
		SUCC,
		FAIL
	}
	
	public static enum ScheduleJobOperation implements JobScheduledCommand {
		START_JOB_MASTER_ROOT_REQUEST,
		START_JOB_SCHEDULER,
	}
	
	@Getter
	@RequiredArgsConstructor
	public static class JobScheduleTimeout implements JobScheduledCommand {
		private final int jobCircle;
		private final LocalDateTime timeoutedAt;
	}
	
	public static class JobCachedInfo implements JobScheduledCommand {
		public final Optional<String> value;
		public JobCachedInfo(Optional<String> value) {
			this.value = value;
		}
	}
	
	@ToString
	@RequiredArgsConstructor
	public static class TaskWorkerListingResponse implements JobScheduledCommand {
		
		final Receptionist.Listing listing;
		
		public Set<ActorRef<JobWorkerCommand>> getServices(
				ServiceKey<JobWorkerCommand> serviceKey) {
			return listing.getServiceInstances(serviceKey);
		}
		
	}
	
}