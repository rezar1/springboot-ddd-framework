package com.zero.ddd.akka.cluster.job.actor;

import java.time.LocalDateTime;
import java.util.BitSet;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.zero.ddd.akka.cluster.core.helper.ProtoBufSerializeUtils;
import com.zero.ddd.akka.cluster.core.initializer.serializer.SelfProtoBufObject;
import com.zero.ddd.akka.cluster.job.actor.JobScheduler.CompleteJobTask;
import com.zero.ddd.akka.cluster.job.actor.JobScheduler.JobScheduledCommand;
import com.zero.ddd.akka.cluster.job.actor.JobScheduler.JobTaskStartedRunning;
import com.zero.ddd.akka.cluster.job.actor.JobScheduler.MapTask;
import com.zero.ddd.akka.cluster.job.actor.JobScheduler.ScheduleJobOperation;
import com.zero.ddd.akka.cluster.job.definition.JobEndpoint;
import com.zero.ddd.akka.cluster.job.definition.JobTaskContext;
import com.zero.ddd.akka.cluster.job.definition.TaskResult;
import com.zero.ddd.akka.cluster.job.definition.TaskResult.MapedTask;
import com.zero.ddd.akka.cluster.job.model.vo.TaskIdAttachInfo;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import akka.actor.typed.receptionist.Receptionist;
import akka.cluster.sharding.typed.ShardingEnvelope;
import akka.cluster.sharding.typed.delivery.ShardingProducerController;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2023-04-13 06:43:33
 * @Desc 些年若许,不负芳华.
 * 
 */
@Slf4j(topic = "job")
public class JobWorker {
	
	private static class WrappedRequestNext implements JobWorkerCommand {
		final ShardingProducerController.RequestNext<JobScheduledCommand> next;
		
		private WrappedRequestNext(
				ShardingProducerController.RequestNext<JobScheduledCommand> next) {
			this.next = next;
		}
	}
	
	public static Behavior<JobWorkerCommand> create(
			JobEndpoint job,
			ActorRef<ShardingProducerController.Command<JobScheduledCommand>> producerController) {
		return Init.create(job, producerController);
	}
	
	public static class Init extends AbstractBehavior<JobWorkerCommand> {

		static Behavior<JobWorkerCommand> create(
				JobEndpoint job,
				ActorRef<ShardingProducerController.Command<JobScheduledCommand>> producerController) {
			return Behaviors.setup(context -> {
				ActorRef<ShardingProducerController.RequestNext<JobScheduledCommand>> requestNextAdapter = 
						context.messageAdapter(
								ShardingProducerController.requestNextClass(),
								WrappedRequestNext::new);
				producerController.tell(
						new ShardingProducerController.Start<>(
								requestNextAdapter));
				return new Init(job, context);
			});
		}
		
		private JobEndpoint job;

		private Init(
				JobEndpoint job,
				ActorContext<JobWorkerCommand> context) {
			super(context);
			this.job = job;
		}

		@Override
		public Receive<JobWorkerCommand> createReceive() {
			return newReceiveBuilder()
					.onMessage(WrappedRequestNext.class, w -> Active.create(this.job, w.next))
					.build();
		}
	}
	
	static class Active extends AbstractBehavior<JobWorkerCommand> {

		private final JobEndpoint job;
		private final String workerShowName;
		private ShardingProducerController.RequestNext<JobScheduledCommand> requestNext;
		private int currentTaskCircle = -1;
		private final BitSet taskIdFilter = new BitSet(); 

		static Behavior<JobWorkerCommand> create(
				JobEndpoint job,
				ShardingProducerController.RequestNext<JobScheduledCommand> requestNext) {
			return Behaviors.setup(
					context -> 
						new Active(
								job,
								context, 
								requestNext));
		}

		private Active(
				JobEndpoint job,
				ActorContext<JobWorkerCommand> context,
				ShardingProducerController.RequestNext<JobScheduledCommand> requestNext) {
			super(context);
			this.job = job;
			this.requestNext = requestNext;
			this.startServiceRegiste();
			this.workerShowName = context.getSelf().path().name();
			log.info("作业:[{}]\t工作节点:[{}]启动完成", this.job.jobShowName(), this.workerShowName);
		}

		private Behavior<JobWorkerCommand> pingScheduler() {
			this.requestNext.sendNextTo()
			.tell(
					new ShardingEnvelope<JobScheduler.JobScheduledCommand>(
							this.job.getJobName(),
							ScheduleJobOperation.START_JOB_SCHEDULER));
			return this;
		}

		private void startServiceRegiste() {
			super.getContext()
			.getSystem()
			.receptionist()
			.tell(
					Receptionist.register(
							ServiceKeyHolder.jobWorkerServiceKey(
									this.job.getJobName()),
							super.getContext().getSelf()));
		}

		protected <T> List<byte[]> serialToBytes(List<T> tasks) {
			return tasks.stream()
					.map(task -> {
						return ProtoBufSerializeUtils.serialize(task);
					})
					.collect(Collectors.toList());
		}

		@Override
		public Receive<JobWorkerCommand> createReceive() {
			return newReceiveBuilder()
					.onMessage(StartJobTaskCommand.class, this::startTaskCommand)
					.onMessage(WrappedRequestNext.class, this::onRequestNext)
					.onMessage(CompleteTask.class, this::completedTask)
					.onMessage(CompleteTaskWithMapMore.class, this::completedTaskWithMapMore)
					.onMessageEquals(JobWorkerState.PING, this::pingScheduler)
					.onAnyMessage(msg -> {
						log.info("unknow message:{}", msg);
						return this;
					})
					.build();
		}

		private Behavior<JobWorkerCommand> onRequestNext(
				WrappedRequestNext w) {
			requestNext = w.next;
			return this;
		}
		
		private Behavior<JobWorkerCommand> startTaskCommand(
				StartJobTaskCommand command) {
			log.info(
					"作业:[{}] 工作节点:[{}] 获取到任务执行命令:[{}]", 
					this.job.jobShowName(), 
					this.workerShowName, 
					command);
			this.answerTaskHasStartRunning(command);
			if (this.taskIdNotExists(
					command.taskIdAttachInfo())) {
				try {
					// TODO 看下实现为可中断的执行流程
					TaskResult invokeJobMethod = 
							this.job.getJobMethodInvoker()
							.invokeJobMethod(
									new JobTaskContext(
											command.getTaskId(),
											command.getTaskType(), 
											command.getTaskBody(),
											command.getStartdAt()));
					if (invokeJobMethod.isMapAnswer()) {
						this.answerMapedTask(
								command, 
								invokeJobMethod.getMapTask());
					} else {
						this.answerTaskCompleted(
								command,
								invokeJobMethod.isSucc(), 
								invokeJobMethod.getResult());
					}
				} catch (Exception e) {
					log.info("作业:[{}] 工作节点:[{}] 任务执行失败:[{}]", this.job.jobShowName(), this.workerShowName, command);
					log.error("任务执行异常:{}", e);
					this.answerTaskCompleted(
							command,
							false, 
							e.getMessage());
				}
			} else {
				log.info(
						"作业:[{}] 工作节点:[{}] 任务ID重复:[{}]", 
						this.job.jobShowName(), 
						this.workerShowName, 
						command.taskId);
				this.answerTaskCompleted(
						command,
						false, 
						"任务重复下发");
			}
			return this;
		}

		private boolean taskIdNotExists(
				Optional<TaskIdAttachInfo> taskIdAttachInfo) {
			return 
					taskIdAttachInfo
					.map(taskIdInfo -> {
						if (this.currentTaskCircle 
								!= taskIdInfo.getJobCircle()) {
							// 新的一轮了, 重置过滤器
							this.taskIdFilter.clear();
						}
						this.currentTaskCircle = 
								taskIdInfo.getJobCircle();
						int taskSeq = 
								taskIdInfo.getTaskSequence();
						boolean hasSet = 
								this.taskIdFilter.get(taskSeq);
						this.taskIdFilter.set(taskSeq);
						return !hasSet;
					})
					// 历史的任务ID可能不是cicle#taskSeq的格式，这种解析不出TaskIdAttachInfo，直接置为有效任务
					.orElse(true);
		}

		private void answerTaskCompleted(
				StartJobTaskCommand command, 
				boolean succ, 
				String result) {
			requestNext.sendNextTo()
			.tell(
					new ShardingEnvelope<JobScheduledCommand>(
							this.job.getJobName(),
							new CompleteJobTask(
									command.taskId,
									result,
									succ)));
			log.info(
					"作业:[{}] 工作节点:[{}] 任务ID:{} 上报执行结果:[成功:{}-result:{}]", 
					this.job.jobShowName(),
					this.workerShowName, 
					command.getTaskId(), 
					succ,
					result);
		}

		private void answerMapedTask(
				StartJobTaskCommand command,
				MapedTask mapTask) {
			requestNext.sendNextTo()
			.tell(
					new ShardingEnvelope<JobScheduledCommand>(
							this.job.getJobName(),
							new MapTask(
									command.taskId,
									mapTask.getTaskType(),
									this.serialToBytes(mapTask.getMapTasks()))));
		}

		private void answerTaskHasStartRunning(
				StartJobTaskCommand command) {
			requestNext.sendNextTo()
			.tell(
					new ShardingEnvelope<JobScheduledCommand>(
							this.job.getJobName(),
							new JobTaskStartedRunning(
									command.getTaskId())));
		}
		
		private Behavior<JobWorkerCommand> completedTask(
				CompleteTask command) {
			requestNext.sendNextTo()
			.tell(
					new ShardingEnvelope<JobScheduledCommand>(
							this.job.getJobName(),
							new CompleteJobTask(
									command.getTaskId(),
									command.getTaskResult(),
									command.isSuccess())));
			return this;
		}
		
		private Behavior<JobWorkerCommand> completedTaskWithMapMore(
				CompleteTaskWithMapMore command) {
			requestNext.sendNextTo()
			.tell(
					new ShardingEnvelope<JobScheduledCommand>(
							this.job.getJobName(),
							new MapTask(
									command.getTaskId(),
									command.getTaskType(),
									command.getMapedTasks())));
			return this;
		}

	}
	
	public static interface JobWorkerCommand extends SelfProtoBufObject {
		
	}
	
	public static enum JobWorkerState implements JobWorkerCommand {
		PING
	}
	
	public static enum Response implements JobWorkerCommand {
		ACCEPTED, REJECTED, MAYBE_ACCEPTED
	}
	
	@Data
	@AllArgsConstructor
	public static class CompleteTask implements JobWorkerCommand {
		private String taskId;
		private boolean success;
		private String taskResult;
	}
	
	@Data
	@AllArgsConstructor
	public static class CompleteTaskWithMapMore implements JobWorkerCommand {
		
		private String taskId;
		private String taskType;
	    private List<byte[]> mapedTasks;
		
	}
	
	@Data
	@NoArgsConstructor
	public static class StartJobTaskCommand implements JobWorkerCommand {
		
		private String taskId;
		private String taskType;
		@JsonIgnore
		private byte[] taskBody;
		private LocalDateTime startdAt;
		
		public StartJobTaskCommand(
				String taskId, 
				String taskType, 
				byte[] taskBody,
				LocalDateTime startdAt) {
			this.taskId = taskId;
			this.taskType = taskType;
			this.taskBody = taskBody;
			this.startdAt = startdAt;
		}
		
		public Optional<TaskIdAttachInfo> taskIdAttachInfo() {
			return TaskIdAttachInfo.parseTaskId(taskId);
		}

		@Override
		public String toString() {
			return "StartJobTaskCommand [taskId=" + taskId + ", taskType=" + taskType + ", taskBodyLen="
					+ (taskBody != null ? taskBody.length : 0) + ", startdAt=" + startdAt + "]";
		}
		
	}

}