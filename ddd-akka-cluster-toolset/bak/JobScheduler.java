package com.zero.ddd.akka.cluster.toolset.task1.actor;

import java.util.Optional;
import java.util.Set;

import com.zero.ddd.akka.cluster.core.initializer.serializer.SelfProtoBufObject;
import com.zero.ddd.akka.cluster.toolset.task.simple.TaskWorkerActor.JobWorkerCommand;
import com.zero.ddd.akka.cluster.toolset.task1.JobDatabase;
import com.zero.ddd.akka.cluster.toolset.task1.jobMaster.AbstractJobMaster.JobMasterCommand;
import com.zero.ddd.akka.cluster.toolset.task1.jobMaster.AbstractJobMaster.JobMasterStartRootRequest;
import com.zero.ddd.akka.cluster.toolset.task1.jobMaster.MapJobMaster;
import com.zero.ddd.akka.cluster.toolset.task1.model.Job;
import com.zero.ddd.akka.cluster.toolset.task1.model.JobInstanceState;
import com.zero.ddd.akka.cluster.toolset.task1.model.vo.JobConfig;
import com.zero.ddd.akka.cluster.toolset.task1.model.vo.JobExecuteType;
import com.zero.ddd.akka.cluster.toolset.task1.model.vo.TaskExecuteStatus;

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
import io.vavr.API;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

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
public class JobScheduler {
	
	public static Behavior<JobScheduledCommand> create(
			Job job, 
			JobDatabase db,
			ActorRef<ConsumerController.Start<JobScheduledCommand>> consumerController) {
		return Init.create(job, db, consumerController);
	}
	
	static class Init extends AbstractBehavior<JobScheduledCommand> {

		private final Job job;
		private final JobDatabase db;
		private final ActorRef<ConsumerController.Start<JobScheduledCommand>> consumerController;

		private Init(
				Job job, 
				JobDatabase db,
				ActorContext<JobScheduledCommand> context, 
				ActorRef<ConsumerController.Start<JobScheduledCommand>> consumerController) {
			super(context);
			this.job = job;
			this.db = db;
			this.consumerController = consumerController;
		}

		static Behavior<JobScheduledCommand> create(
				Job job, 
				JobDatabase db,
				ActorRef<ConsumerController.Start<JobScheduledCommand>> consumerController) {
			return Behaviors.setup(context -> {
				context.pipeToSelf(
						db.loadJobInstance(job.getJobName()),
						(state, exc) -> {
							if (exc == null)
								return new InitialState(state);
							else
								return new DBError(exc);
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
					.onMessage(DBError.class, JobScheduler::onDBError)
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
		
		public static Behavior<JobScheduledCommand> create(
				Job job, 
				JobDatabase db,
				JobInstanceState state) {
			return Behaviors.setup(
					context -> 
						new Active(
								job, 
								state == null ? job.initJobInstance() : state,
								db, 
								context).schedule());
		}
		
		private final Job job;
		private JobInstanceState state;
		private final JobDatabase jobDatabase;
		private final ActorContext<JobScheduledCommand> context;
		private final ServiceKey<JobWorkerCommand> jobServiceKey;
		
		public Active(
				Job job,
				JobInstanceState state,
				JobDatabase jobDatabase,
				ActorContext<JobScheduledCommand> context) {
			this.job = job;
			this.state = state;
			this.context = context;
			this.jobDatabase = jobDatabase;
			this.jobServiceKey = 
					ServiceKey.create(
							JobWorkerCommand.class, 
							"SK-" + this.job.getJobName());
			this.subscribeWorkerService();
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
		
		Behavior<JobScheduledCommand> schedule() {
			if (this.state.duringRunning()) {
				return jobScheduleing();
			}
			return null;
		}
		
		/**
		 * 任务处于调度中
		 * 
		 * @return
		 */
		private Behavior<JobScheduledCommand> jobScheduleing() {
			this.context.scheduleOnce(
					this.state.durationWithNextExecutionTime(), 
					this.context.getSelf(),
					ScheduleJobOperation.START_JOB_MASTER_ROOT_REQUEST);
			return Behaviors.receive(JobScheduledCommand.class)
					.onMessage(CommandDelivery.class, this::onDelivery)
					.onMessage(SaveSuccess.class, this::onSaveSuccess)
					.onMessage(DBError.class, JobScheduler::onDBError)
					.onMessage(TaskWorkerListingResponse.class, this::duringRunningHandleWorkerListingChanged)
					.build();
		}
		
		// 运行时工作节点列表发生变化，将
		private Behavior<JobScheduledCommand> duringRunningHandleWorkerListingChanged(
				TaskWorkerListingResponse changed) {
			Set<ActorRef<JobWorkerCommand>> services = changed.getServices(jobServiceKey);
			return Behaviors.same();
		}

		private void scheduleOnceTrigger() {
			state.configNextExecutionTime(
					this.job.nextExtExecutionTime(
							state.startedAt()));
			this.context.scheduleOnce(
					state.durationWithNextExecutionTime(), 
					this.context.getSelf(),
					ScheduleJobOperation.START_JOB_MASTER_ROOT_REQUEST);
		}

		private Behavior<JobScheduledCommand> onDelivery(
				CommandDelivery delivery) {
			// 两种命令 1: CompleteTask 2: mapTask
			if (delivery.command instanceof CompleteTask) {
				CompleteTask completeTask = (CompleteTask) delivery.command;
				if (state.updateTaskStatus(
						completeTask.getTaskId(), 
						completeTask.getTaskResult(), 
						completeTask.getExecuteStatus())) {
					this.save(state, delivery.confirmTo);
				}
				return Behaviors.same();
			} else {
				return Behaviors.unhandled();
			}
		}
		
		private Behavior<JobScheduledCommand> onSaveSuccess(
				SaveSuccess success) {
			success.confirmTo.tell(
					ConsumerController.confirmed());
			return Behaviors.same();
		}

		private void save(
				JobInstanceState newState, 
				ActorRef<Confirmed> confirmTo) {
			this.context
			.pipeToSelf(
					this.jobDatabase.updateJobInstance(job.getJobName(), newState),
					(state, exc) -> {
						if (exc == null)
							return new SaveSuccess(confirmTo);
						else
							return new DBError(exc);
					});
		}

		// 看下自己实现个workingpull
		/**
		 * 获取所有在线列表，每次任务分发的时候，从池中取出一个可用的worker, 如果没有watch,先watch, 将任务项添加到该worker的执行列表中，然后worker的汇报通过shardingReport
		 * 如果watch通知worker下线了，取出对应的任务执行列表中的任务项(可能存在shardingReport有成功执行的任务项, 看下如何处理), 重新进行分发
		 * shardingReport之后，标记对应的任务项完成，并告知JobMaster指定worker可继续执行任务项
		 * @param jobInstance
		 * @return
		 */
		private Behavior<JobScheduledCommand> jobScheduleing(
				JobInstanceState jobInstance) {
			// 设置jobInstance下次执行时间
			jobInstance.configNextExecutionTime(
					this.job.nextExtExecutionTime(
							jobInstance.startedAt()));
			this.context.scheduleOnce(
					jobInstance.durationWithNextExecutionTime(), 
					this.context.getSelf(),
					ScheduleJobOperation.START_JOB_MASTER_ROOT_REQUEST);
			return Behaviors.receive(JobScheduledCommand.class)
					.onMessage(
							JobMasterCompletedTaskState.class, 
							msg -> {
								jobInstance.completedTask(
										msg == JobMasterCompletedTaskState.SUCC);
								if (jobInstance.needStartNextCircle()) {
									return this.jobScheduleing(jobInstance);
								}
								return Behaviors.same();
							})
					.onMessageEquals(
							ScheduleJobOperation.START_JOB_MASTER_ROOT_REQUEST, 
							() -> {
								// 任务还在运行中，等待任务结束后判断是否继续执行
								if (jobInstance.duringRunning()) {
									return Behaviors.same();
								}
								this.startJobMasterRootRequest(
										jobInstance);
								return Behaviors.same();
							})
					// 如果是worker上报任务状态的事假
					.onMessageEquals(null, null)
					.build();
		}

		/**
		 * 开启JobMaster执行调度
		 * @param jobInstance
		 */
		private void startJobMasterRootRequest(
				JobInstanceState jobInstance) {
			this.logPreJobInstance(jobInstance);
			jobInstance.startNewCircle();
			// 通知执行新一轮的调度
			this.jobMasterRef.tell(
					new JobMasterStartRootRequest(
							this.job.jobConfig()));
		}

		private void logPreJobInstance(
				JobInstanceState jobInstance) {
			
		}

		private void startJobMaster() {
			JobExecuteType jobExecuteType = 
					this.job.getJobExecuteType();
			JobConfig jobConfig = this.job.jobConfig();
			Behavior<JobMasterCommand> behavior = 
					API.Match(
							jobExecuteType)
					.of(
							API.Case(API.$(JobExecuteType.BROADCAST), MapJobMaster.create(jobConfig)),
							API.Case(API.$(JobExecuteType.MAP), MapJobMaster.create(jobConfig)),
							API.Case(API.$(JobExecuteType.MAP_REDUCE), MapJobMaster.create(jobConfig)),
							API.Case(API.$(JobExecuteType.SHARDING), MapJobMaster.create(jobConfig)),
							API.Case(API.$(JobExecuteType.STANDALONE), MapJobMaster.create(jobConfig)));
		}

	}
	
	public static interface JobScheduledCommand extends SelfProtoBufObject {
		
	}
	
	@Data
	@NoArgsConstructor
	public static class CompleteTask implements JobScheduledCommand {
		private String taskId;
		private String taskResult;
		private TaskExecuteStatus executeStatus;

		public CompleteTask(String item) {
			this.taskId = item;
		}
	}

	private static class InitialState implements JobScheduledCommand {
		final JobInstanceState state;
		private InitialState(
				JobInstanceState state) {
			this.state = state;
		}
	}

	private static class SaveSuccess implements JobScheduledCommand {
		final ActorRef<ConsumerController.Confirmed> confirmTo;
		
		private SaveSuccess(ActorRef<ConsumerController.Confirmed> confirmTo) {
			this.confirmTo = confirmTo;
		}
	}

	private static class DBError implements JobScheduledCommand {
		final Exception cause;

		private DBError(Throwable cause) {
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
			DBError error) throws Exception {
		throw error.cause;
	}
	
	public static enum JobMasterCompletedTaskState implements JobScheduledCommand {
		SUCC,
		FAIL
	}
	
	public static enum ScheduleJobOperation implements JobScheduledCommand {
		START_JOB_MASTER_ROOT_REQUEST,
		JOB_MASTER_START_TASK,
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