package com.zero.ddd.akka.cluster.toolset.task1.actor;

import java.util.Optional;

import com.zero.ddd.akka.cluster.core.initializer.serializer.SelfProtoBufObject;
import com.zero.ddd.akka.cluster.toolset.task1.JobDatabase;
import com.zero.ddd.akka.cluster.toolset.task1.actor.JobReplicatedCache.Cached;
import com.zero.ddd.akka.cluster.toolset.task1.actor.JobReplicatedCache.GetFromCache;
import com.zero.ddd.akka.cluster.toolset.task1.actor.JobReplicatedCache.JobCacheCommand;
import com.zero.ddd.akka.cluster.toolset.task1.jobMaster.AbstractJobMaster.JobMasterCommand;
import com.zero.ddd.akka.cluster.toolset.task1.jobMaster.AbstractJobMaster.JobMasterStartRootRequest;
import com.zero.ddd.akka.cluster.toolset.task1.jobMaster.MapJobMaster;
import com.zero.ddd.akka.cluster.toolset.task1.model.Job;
import com.zero.ddd.akka.cluster.toolset.task1.model.JobInstance;
import com.zero.ddd.akka.cluster.toolset.task1.model.vo.JobConfig;
import com.zero.ddd.akka.cluster.toolset.task1.model.vo.JobExecuteType;
import com.zero.helper.JacksonUtil;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.StashBuffer;
import io.vavr.API;

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
public class JobScheduled {
	
	private final Job job;
	private final JobDatabase jobDatabase;
	private final String jobInstanceCacheKey;
	private final ActorRef<JobCacheCommand> cacheRef;
	private final StashBuffer<JobScheduledCommand> stash;
	private final ActorContext<JobScheduledCommand> context;
	private ActorRef<JobMasterCommand> jobMasterRef;
	
	public JobScheduled(
			Job job,
			JobDatabase jobDatabase,
			ActorRef<JobCacheCommand> cacheRef,
			StashBuffer<JobScheduledCommand> stash,
			ActorContext<JobScheduledCommand> context) {
		this.job = job;
		this.stash = stash;
		this.context = context;
		this.cacheRef = cacheRef;
		this.jobDatabase = jobDatabase;
		this.jobInstanceCacheKey = "JobInstance-" + this.job.getJobName();
	}
	
	public Behavior<JobScheduledCommand> init() {
		this.startJobMaster();
		this.cacheRef.tell(
				new GetFromCache(
						this.jobInstanceCacheKey, 
						this.context.messageAdapter(
								Cached.class, 
								cached -> {
									return new JobCachedInfo(
											cached.getValue());
								})));
		JobInstance jobInstance = 
				this.jobDatabase.queryJobInstance(
						this.job.getJobName())
				.orElseGet(() -> {
					return this.job.initJobInstance();
				});
		return this.jobScheduleing(jobInstance);
	}
	
	/**
	 * 启动后，尝试从分布式缓存中恢复状态
	 */
	private Behavior<JobScheduledCommand> recoveryJobInstance() {
		return Behaviors.receive(
				JobScheduledCommand.class)
				.onMessage(
						JobCachedInfo.class,
						cacheInfo -> {
							JobInstance jobInstance = 
									cacheInfo.value
									.map(this::deserializeToJobInstance)
									.orElseGet(() -> {
										return this.job.initJobInstance();
									});
							// 如果任务还在运行中，应该是通知TaskMaster
							if (jobInstance.duringRunning()) {
								
							}
							return this.stash.unstashAll(
									jobScheduleing(
											jobInstance));
						})
				.onAnyMessage(msg -> {
					this.stash.stash(msg);
					return Behaviors.same();
				})
				.build();
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
			JobInstance jobInstance) {
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
			JobInstance jobInstance) {
		this.logPreJobInstance(jobInstance);
		jobInstance.startNewCircle();
		// 通知执行新一轮的调度
		this.jobMasterRef.tell(
				new JobMasterStartRootRequest(
						this.job.jobConfig()));
	}

	private void logPreJobInstance(
			JobInstance jobInstance) {
		
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
		this.jobMasterRef = 
				this.context.spawn(
						behavior, 
						"JobMaster-" + jobExecuteType.name());
	}

	private JobInstance deserializeToJobInstance(String json) {
		return JacksonUtil.str2ObjNoError(json, JobInstance.class);
	}
	
	public static interface JobScheduledCommand extends SelfProtoBufObject {
		
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
	
}