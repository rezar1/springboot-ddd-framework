package com.zero.ddd.akka.cluster.toolset.task.simple;

import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import com.zero.ddd.akka.cluster.toolset.task.scheduled.INextExecutionTimeCalculator;
import com.zero.ddd.akka.cluster.toolset.task.simple.JobReplicatedCache.Cached;
import com.zero.ddd.akka.cluster.toolset.task.simple.JobReplicatedCache.GetFromCache;
import com.zero.ddd.akka.cluster.toolset.task.simple.JobReplicatedCache.JobCacheCommand;
import com.zero.ddd.akka.cluster.toolset.task.simple.JobReplicatedCache.PutInCache;
import com.zero.ddd.akka.cluster.toolset.task.simple.TaskWorkerActor.JobWorkerCommand;
import com.zero.ddd.akka.cluster.toolset.task.simple.model.JobState;
import com.zero.ddd.akka.cluster.toolset.task.simple.model.vo.CollectionDiff;
import com.zero.helper.GU;
import com.zero.helper.JacksonUtil;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.BehaviorBuilder;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.receptionist.Receptionist;
import akka.actor.typed.receptionist.Receptionist.Listing;
import akka.actor.typed.receptionist.ServiceKey;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2023-04-13 04:30:19
 * @Desc 些年若许,不负芳华.
 *
 * 集群单例
 * 
 * 需要分布式缓存支持读取和更新任务开始执行时间和完成执行时间
 * 每次调度之后，重新计算下一次调度时间
 * 单次执行必须要有结果--->即任务提交，先进行存储再通知给客户端，客户端
 * 
 */
public class JobManager {
	
	public static final StartJobRequest START_CIRCLE_JOB_REQ = new StartJobRequest();
	
	private final String jobName;
	private final String jobCacheKey;
	private final int minExecutorCount;
	private final Duration jobExecuteTimeout;
	private final ActorContext<JobCommand> context;
	private final ActorRef<JobCacheCommand> jobCacheActorRef;
	private final ServiceKey<JobWorkerCommand> jobServiceKey;
	private final INextExecutionTimeCalculator iNextExecuteTimeCalculator;
	private final Set<ActorRef<JobWorkerCommand>> curOnlineWorker = new HashSet<>();
	private final Map<String, ActorRef<JobWorkerCommand>> allTaskWorkerCache = new HashMap<>();
	
	private JobState jobState;
	
	public JobManager(
			String jobName,
			int minExecutorCount,
			Duration jobExecuteTimeout,
			INextExecutionTimeCalculator iNextExecuteTimeCalculator,
			ActorRef<JobCacheCommand> jobCacheActorRef,
			ActorContext<JobCommand> context) {
		this.context = context;
		this.jobName = jobName;
		this.jobCacheKey = "Job-" + this.jobName;
		this.minExecutorCount = minExecutorCount;
		this.jobCacheActorRef = jobCacheActorRef;
		this.jobExecuteTimeout = jobExecuteTimeout;
		this.iNextExecuteTimeCalculator = iNextExecuteTimeCalculator;
		this.jobServiceKey = ServiceKey.create(JobWorkerCommand.class, "SK-" + jobName);
	}

	/**
	 * 流程，先查询分布式缓存，
	 * 1. 如果有设置的作业数据，
	 * 2. 
	 */
	public Behavior<JobCommand> initJob() {
		this.subscribeTaskService();
		this.jobCacheActorRef.tell(
				new GetFromCache(
						this.jobCacheKey, 
						this.context.messageAdapter(
								Cached.class, 
								cached -> {
									return new JobCachedInfo(
											cached.getValue());
								})));
		return waitJobStateInited();
	}
	
	private Behavior<JobCommand> waitJobStateInited() {
		InitState initState = new InitState();
		return Behaviors
				.receive(JobCommand.class)
				.onMessage(
						TaskWorkerListingResponse.class, 
						listing -> {
							this.refreshOnlineWorker(listing);
							if (GU.notNullAndEmpty(
									this.allTaskWorkerCache)) {
								initState.onlineServicesInited();
							}
							if (initState.inited()) {
								return jobDuringRunning();
							}
							return Behaviors.same();
						})
				.onMessage(
						JobCachedInfo.class, 
						jobCacheInfo -> {
							this.jobState = 
									jobCacheInfo.value
									.map(jsonData -> {
										return JacksonUtil.str2ObjNoError(
												jsonData, 
												JobState.class);
									})
									.orElseGet(() -> {
										return new JobState(this.jobName);
									});
							initState.jobInstanceInited();
							if (initState.inited()) {
								return jobDuringRunning();
							}
							return Behaviors.same();
						})
				.build();
	}
	
	/**
	 * 任务状态初始化完成，检查任务执行情况
	 * 
	 * @return
	 */
	private Behavior<JobCommand> jobDuringRunning() {
		if (!this.jobState.duringRunning()
				|| this.jobState.isRunningTimeout(this.jobExecuteTimeout)) {
			return this.jobEnd();
		}
		// 任务开启执行了，监听map事件和task执行结果上报
		return this.workerListChangedBehavior()
				// 处理map
				.onMessage(
						JobMapTaskRequest.class, 
						mapReq -> {
							this.jobState.taskCompleted(
									mapReq.getTaskId());
							this.assignNormalTask(
									mapReq.mapTaskParamsList);
							return this.checkJobState();
						})
				// 处理结果
				.onMessage(
						JobTaskResultReport.class,
						result -> {
							this.jobState.taskCompleted(
									result.getTaskId(),
									result.success,
									result.message);
							return this.checkJobState();
						})
				.build();
	}

	private Behavior<JobCommand> checkJobState() {
		if (this.jobState.isCurrentCirleJobComplted()) {
			return this.jobEnd();
		}
		this.updateJobStateCache();
		return Behaviors.same();
	}
	
	// 刷新当前的jobState到分布式缓存中, writeMajority
	private void updateJobStateCache() {
		this.jobCacheActorRef.tell(
				new PutInCache(
						this.jobCacheKey,
						JacksonUtil.obj2Str(
								this.jobState)));
	}

	private void refreshTaskStateByCache() {
		Map<String, ActorRef<JobWorkerCommand>> onlineRefMap = 
				this.curOnlineWorker.stream()
				.collect(Collectors.toMap(this::jobWorkerAddress, ref -> ref));
		this.jobState.unReportResultTask()
		.stream()
		.filter(unResultTask -> {
			// 如果在线的服务列表不包含任务执行所在的节点，读一次缓存并最终更新
			return 
					!onlineRefMap.containsKey(unResultTask.getLeft());
		})
		.map(unResultTask -> {
			return unResultTask.getRight();
		})
		.collect(Collectors.toList());
	}

	private Behavior<JobCommand> jobEnd() {
		this.jobState.startNewCircle();
		this.updateJobStateCache();
		Duration nextExecutionWaitDuration = 
				this.iNextExecuteTimeCalculator.nextExecutionWaitDuration(
						this.jobState.jobCalculatorDate());
		if (nextExecutionWaitDuration.isNegative()
				|| nextExecutionWaitDuration.isZero()) {
			return this.startNewJob();
		}
		return waitNextCircleJobStart(
				nextExecutionWaitDuration);
	}

	private Behavior<JobCommand> waitNextCircleJobStart(
			Duration nextExecutionWaitDuration) {
		context.scheduleOnce(
				nextExecutionWaitDuration,
				context.getSelf(), 
				START_CIRCLE_JOB_REQ);
		return this.workerListChangedBehavior()
				.onMessage(
						StartJobRequest.class, 
						msg -> {
							return this.startNewJob();
						})
				.onAnyMessage(msg -> {
					return Behaviors.ignore();
				})
				.build();
	}

	private Behavior<JobCommand> startNewJob() {
		if (!this.workerCountSatisfy()) {
			return 
					this.waitWorkerCountSatisfy();
		}
		assignRootTask();
		return this.jobDuringRunning();
	}

	private void assignRootTask() {
		ActorRef<JobWorkerCommand> jobWorker = 
				this.selectJobWorker();
		jobWorker.tell(
				this.jobState.assignRootTask(
						this.jobWorkerAddress(jobWorker)));
	}
	
	private void assignNormalTask(
			List<String> taskParamsList) {
		taskParamsList
		.forEach(taskParams -> {
			ActorRef<JobWorkerCommand> jobWorker = 
					this.selectJobWorker();
			jobWorker.tell(
					this.jobState.assignRootTask(
							this.jobWorkerAddress(jobWorker)));
		});
	}

	private String jobWorkerAddress(
			ActorRef<JobWorkerCommand> jobWorker) {
		return jobWorker.path().address().hostPort();
	}

	// TODO 看使用什么算法分配节点给任务
	private ActorRef<JobWorkerCommand> selectJobWorker() {
		return null;
	}

	private Behavior<JobCommand> waitWorkerCountSatisfy() {
		return this.workerListChangedBehavior(() -> {
			if (!this.workerCountSatisfy()) {
				return Behaviors.same();
			}
			return this.startNewJob();
		}).build();
	}

	private boolean workerCountSatisfy() {
		return this.allTaskWorkerCache.size() >= this.minExecutorCount;
	}

	private BehaviorBuilder<JobCommand> workerListChangedBehavior() {
		return this.workerListChangedBehavior(Behaviors::same);
	}
	
	private BehaviorBuilder<JobCommand> workerListChangedBehavior(
			Supplier<Behavior<JobCommand>> behaviorSupplier) {
		return Behaviors
				.receive(JobCommand.class)
				.onMessage(
						TaskWorkerListingResponse.class, 
						listing -> {
							this.refreshOnlineWorker(listing);
							return behaviorSupplier.get();
						});
	}
	
	public void refreshOnlineWorker(
			TaskWorkerListingResponse resp) {
		Set<ActorRef<JobWorkerCommand>> services = 
				resp.getServices(this.jobServiceKey);
		if (this.duringJobRunningState()) {
			Set<ActorRef<JobWorkerCommand>> deletedSet = 
					CollectionDiff.getDelete(
							this.curOnlineWorker, 
							services);
			this.tryRebalance(deletedSet);
		}
		this.curOnlineWorker.clear();
		this.curOnlineWorker.addAll(services);
	}
	
	/**
	 * @param deletedSet
	 */
	private void tryRebalance(
			Set<ActorRef<JobWorkerCommand>> deletedSet) {
	}

	private boolean duringJobRunningState() {
		return this.jobState != null
				&& this.jobState.duringRunning();
	}

	/**
	 * 订阅当前job所有执行节点服务列表的变更
	 * 
	 * @param taskName
	 */
	private void subscribeTaskService() {
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

	public static class StartJobRequest implements JobCommand {
		
	}
	
	@ToString
	@RequiredArgsConstructor
	public static class TaskWorkerListingResponse implements JobCommand {
		
		final Receptionist.Listing listing;
		
		public Set<ActorRef<JobWorkerCommand>> getServices(
				ServiceKey<JobWorkerCommand> serviceKey) {
			return listing.getServiceInstances(serviceKey);
		}
		
	}
	
	public static class JobCachedInfo implements JobCommand {
		public final Optional<String> value;
		public JobCachedInfo(Optional<String> value) {
			this.value = value;
		}
	}
	
	// 工作节点map出任务列表的请求
	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class JobMapTaskRequest implements JobCommand {
		private String taskId;
		private List<String> mapTaskParamsList;
	}
	
	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class JobTaskResultReport implements JobCommand {
		private String taskId;
		private boolean success;
		private String message;
	}
	
	public static class InitState implements JobCommand {
		private boolean jobInstanceInited;
		private boolean onlineServicesInited;
		public void jobInstanceInited() {
			this.jobInstanceInited = true;
		}
		public void onlineServicesInited() {
			this.onlineServicesInited = true;
		}
		public boolean inited() {
			return this.jobInstanceInited && this.onlineServicesInited;
		}
	}

}