package com.zero.ddd.akka.cluster.job.model;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.google.common.collect.Sets;
import com.zero.ddd.akka.cluster.core.helper.ProtoBufSerializeUtils;
import com.zero.ddd.akka.cluster.core.initializer.serializer.SelfProtoBufObject;
import com.zero.ddd.akka.cluster.job.constants.Constants;
import com.zero.ddd.akka.cluster.job.model.vo.JobExecuteType;
import com.zero.ddd.akka.cluster.job.model.vo.JobStatus;
import com.zero.ddd.akka.cluster.job.model.vo.ScheduledTimeType;
import com.zero.ddd.akka.cluster.job.model.vo.ShardingRequest;
import com.zero.ddd.akka.cluster.job.model.vo.TaskExecuteStatus;
import com.zero.helper.GU;
import com.zero.helper.JacksonUtil;
import com.zero.helper.LocalDateTimeSerializer;

import io.protostuff.Exclude;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2023-04-17 11:06:49
 * @Desc 些年若许,不负芳华.
 *
 */
@Data
@Slf4j(topic = "job")
@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
public class JobInstanceState implements SelfProtoBufObject, Serializable, Cloneable {
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 8219957220704972412L;

	private static final String NO_TASK_RESULT = null;
	
	// 运行状态
	private int jobCircle;
	private long version;
	private long taskIdIndex;
	@JsonSerialize(
			using = LocalDateTimeSerializer.class)
	private LocalDateTime startedAt;
	@JsonSerialize(
			using = LocalDateTimeSerializer.class)
	private LocalDateTime completedAt;
	private JobStatus jobStatus;
	private List<JobTask> jobAssignedTask;
	private int taskCount;
	private boolean duringWaitStartRootTask;
	
	// 当前执行任务的worker
	// 运行中的每个worker对应的工作任务列表|保证每个工作者同时只处理一个任务
	@Exclude
	@JsonIgnore
	private transient Map<String, String> workerAssignedJobTaskId = new HashMap<>();
	@Exclude
	@JsonIgnore
	private transient Map<String, JobTask> taskId2Job = new HashMap<>();
	@Exclude
	@JsonIgnore
	private transient String jobName;
	
	public JobInstanceState() {
		this.jobCircle = 0;
		this.startedAt = LocalDateTime.now();
		this.jobStatus = JobStatus.INIT;
		this.jobAssignedTask = new ArrayList<>();
	}
	
	/**
	 * 初始化当前带完成任务项分配到的工作节点，以及cache
	 */
	public void init(
			String jobName) {
		this.jobName = jobName;
		if (!this.duringRunning()) {
			return;
		}
		this.taskId2Job.clear();
		this.workerAssignedJobTaskId.clear();
		this.jobAssignedTask.stream()
		.filter(task -> {
			return task.notCompleted();
		})
		.forEach(task -> {
			log.info("[{}] 初始化未完成的任务\tTaskID:[{}] assignedTo:[{}]", this.jobName, task.getTaskId(), task.getWorker());
			this.taskId2Job.put(
					task.getTaskId(),
					task);
			this.workerAssignedJobTaskId.put(
					task.getWorker(), 
					task.getTaskId());
		});
	}
	
	public boolean duringRunning() {
		return this.jobStatus == JobStatus.RUNNING;
	}

	public LocalDateTime startedAt() {
		return this.startedAt;
	}

	public void startRunning() {
		this.startedAt = LocalDateTime.now();
		this.jobStatus = JobStatus.RUNNING;
	}

	public void completedTask() {
		this.completedAt = LocalDateTime.now();
		this.jobStatus = 
				this.hasFailuredTask() ? JobStatus.FAIL : JobStatus.SUCC;
		this.clearJobTask();
	}

	private boolean hasFailuredTask() {
		return this.jobAssignedTask.stream()
				.filter(task -> {
					return task.isFail();
				})
				.findAny()
				.isPresent();
	}

	private void clearJobTask() {
		this.jobAssignedTask.clear();
		this.workerAssignedJobTaskId.clear();
		this.taskId2Job.clear();
	}

	public void startNewCircle() {
		this.jobCircle ++;
		this.taskIdIndex = 0;
		this.startedAt = LocalDateTime.now();
		this.completedAt = null;
		this.taskId2Job.clear();
		this.workerAssignedJobTaskId.clear();
		this.jobStatus = JobStatus.RUNNING;
	}
	
	public boolean containJobTask(
			String taskId) {
		return this.taskId2Job.containsKey(taskId);
	}
	
	public void jobTaskStartRunning(
			String taskId) {
		JobTask jobTask = 
				this.taskId2Job.get(taskId);
		jobTask.startRunning();
	}

	/**
	 * 上报某个job已经完成了, 需要判断当前job是不是完成所有任务了
	 * 
	 * @param taskId
	 * @param taskResult
	 * @param executeSucc
	 */
	public void completeJobTask(
			String taskId,
			String taskResult, 
			boolean executeSucc) {
		JobTask jobTask = 
				this.taskId2Job.remove(taskId);
		jobTask.completeTask(
				taskResult, 
				executeSucc ? 
						TaskExecuteStatus.SUCC : TaskExecuteStatus.FAIL);
		this.workerAssignedJobTaskId.remove(
				jobTask.getWorker());
		this.taskCount --;
	}

	public boolean isCompleted() {
		return this.taskCount == 0;
	}

	public void mapJobTask(
			String parentTaskId,
			String taskType,
			List<byte[]> tasks) {
		this.completeJobTask(
				parentTaskId, 
				NO_TASK_RESULT, 
				true);
		tasks.stream()
		.forEach(taskParams -> {
			this.storeNewJobTask(
					parentTaskId,
					taskType,
					taskParams);
		});
	}

	private JobTask storeNewJobTask(
			String parentTaskId, 
			String taskType,
			byte[] taskBody) {
		JobTask jobTask = 
				new JobTask(
						this.taskId(),
						parentTaskId, 
						taskType,
						taskBody);
		this.jobAssignedTask.add(jobTask);
		this.taskCount ++;
		return jobTask;
	}
	
	private JobTask storeNewJobTask(
			String parentTaskId, 
			String taskType,
			Object taskBody) {
		return this.storeNewJobTask(
				parentTaskId, 
				taskType,
				ProtoBufSerializeUtils.serialize(taskBody));
	}

	private String taskId() {
		return this.jobCircle + "#" + (this.taskIdIndex ++);
	}

	public void refreshOnlineWorker(
			Set<String> currentOnlieWorker) {
		if (this.duringWaitStartRootTask) {
			// 之前没有在线节点分配任务，延迟到此时开始分配
			return;
		}
		// 获取下线节点上分配的任务
		Sets.newHashSet(
				this.workerAssignedJobTaskId.keySet())
		.stream()
		.filter(assignedTaskWorker -> {
			return !currentOnlieWorker.contains(assignedTaskWorker);
		})
		.forEach(offlineWorker -> {
			try {
				String needRebalanceTaskId = 
						this.workerAssignedJobTaskId.remove(
								offlineWorker);
				JobTask jobAssignedTask = 
						this.taskId2Job.get(
								needRebalanceTaskId);
				jobAssignedTask.resetToWaitAssign();
				log.info("[{}] 下线节点:[{}]的任务\tTaskID:[{}] 将重置为待分配状态", this.jobName, offlineWorker, needRebalanceTaskId);
			} catch (Exception e) {
				log.error("error while refreshOnlineWorker:{}", e);
			}
		});
	}
	
	public List<JobTask> waitDispatchTask(
			Set<String> currentOnlineWorker) {
		currentOnlineWorker = 
				new HashSet<>(
						currentOnlineWorker);
		// 移除已经分配的
		currentOnlineWorker.removeAll(
				this.workerAssignedJobTaskId.keySet());
		if (GU.isNullOrEmpty(currentOnlineWorker)) {
			return GU.emptyList();
		}
		List<JobTask> waitAssignTask = 
				this.waitAssignTask(
						currentOnlineWorker.size());
		if (GU.isNullOrEmpty(waitAssignTask)) {
			return GU.emptyList();
		}
		Iterator<String> iterator = 
				currentOnlineWorker.iterator();
		return 
				waitAssignTask.stream()
				.filter(task -> iterator.hasNext())
				.map(task -> {
					task.assignTo(iterator.next());
					this.workerAssignedJobTaskId.put(
							task.getWorker(), 
							task.getTaskId());
					this.taskId2Job.put(
							task.getTaskId(),
							task);
					return task;
				})
				.collect(Collectors.toList());
	}
	
	private List<JobTask> waitAssignTask(int size) {
		return this.jobAssignedTask.stream()
				.filter(task -> {
					return task.waitAssign();
				})
				.sorted(
						Comparator.comparing(
								JobTask::getStartedAt))
				.limit(size)
				.collect(Collectors.toList());
	}

	/**
	 * 开启RootTask
	 * 
	 * @param keySet
	 */
	public void startRootTask(
			String jobParams,
			JobExecuteType jobExecuteType,
			int onlineWorkerSize) {
		this.startNewCircle();
		if (onlineWorkerSize == 0) {
			log.info("[{}] 当前在线执行节点数为空，等待执行节点上线后再执行RootTask", this.jobName);
			this.waitStartRootTask();
			return;
		}
		this.duringWaitStartRootTask = false;
		if (jobExecuteType == JobExecuteType.BROADCAST) {
			this.startBroadcastRootTask(
					jobParams,
					onlineWorkerSize);
		} else if (jobExecuteType == JobExecuteType.STANDALONE) {
			this.startStandaloneRootTask(
					jobParams);
		} else if (jobExecuteType == JobExecuteType.SHARDING) {
			this.startShardingRootTask(
					jobParams,
					onlineWorkerSize);
		} else if (jobExecuteType == JobExecuteType.MAP) {
			this.startMapReduceRootTask(
					jobParams);
		}
	}

	private void waitStartRootTask() {
		this.duringWaitStartRootTask = true;
	}

	private void startMapReduceRootTask(
			String jobParams) {
		this.storeNewJobTask(
				rootTaskId(),
				Constants.ROOT_TASK_TYPE,
				jobParams.getBytes());
	}

	private void startShardingRootTask(
			String jobParams, 
			int onlineWorkerSize) {
		if (onlineWorkerSize == 0) {
			return;
		}
		jobParams = 
				GU.isNullOrEmpty(jobParams) ? "" : jobParams;
		Map<Long, String> shardingMap =
				Arrays.asList(
						StringUtils.split(jobParams, "&"))
				.stream()
				.map(paramItem -> {
					String[] params = 
							StringUtils.split(paramItem, "=");
					return Pair.of(
							Long.valueOf(params[0]), 
							params[1]);
				})
				.collect(
						Collectors.toMap(
								Pair::getKey, 
								Pair::getValue));
		String rootTaskId = this.rootTaskId();
		IntStream.range(0, onlineWorkerSize)
		.forEach(index -> {
			this.storeNewJobTask(
					rootTaskId,
					Constants.ROOT_TASK_TYPE,
					new ShardingRequest(
							(long)index,
							shardingMap.get((long)index),
							onlineWorkerSize));
		});
	}

	private void startStandaloneRootTask(
			String jobParams) {
		this.storeNewJobTask(
				rootTaskId(),
				Constants.ROOT_TASK_TYPE,
				jobParams.getBytes());
	}

	private void startBroadcastRootTask(
			String jobParams, 
			int onlineWorkerSize) {
		String rootTaskId = this.rootTaskId();
		IntStream.range(0, onlineWorkerSize)
		.forEach(index -> {
			this.storeNewJobTask(
					rootTaskId,
					Constants.ROOT_TASK_TYPE,
					jobParams.getBytes());
		});
	}

	private String rootTaskId() {
		return this.taskId();
	}

	public void incrVersion() {
		this.version ++;
	}

	public String formatStartedAt() {
		return this.startedAt.format(
				DateTimeFormatter.ofPattern(
						"yyyy-MM-dd HH:mm:ss",
						Locale.CHINESE));
	}

	public String snapshotAssignedTask() {
		return JacksonUtil.obj2Str(this.jobAssignedTask);
	}
	
	public List<JobTask> assignedButNotRunningTask() {
		return this.jobAssignedTask
				.stream()
				.filter(task -> {
					return task.justAssigned();
				})
				.collect(Collectors.toList());
	}
	
	public boolean existsAssignedButNotRunningTask() {
		return this.jobAssignedTask.stream()
				.filter(task -> {
					return task.justAssigned();
				})
				.findAny()
				.isPresent();
	}

	/**
	 * 修复原因:
	 * 1. 之前版本使用Protobuf序列化，taskId2Job字段没有排除，导致其也序列化到分布式存储中了
	 * 2. 同一个内存那种taskId2Job和jobAssignedTask的task实例应该是同一个，但通过protobuf反序列化后，两者的task实例就不一致了
	 * 3. 某次worker下线，影响到的task的状态变更为待分配，但由于实例不一致，taskId2Job的task的状态变更了，但jobAssignedTask的没有变更(仍然是RUNNING)
	 * 4. 最终导致通过jobAssignedTask查找待分配任务的流程失败，这个处于运行中的task无法再次分配
	 */
	public void fixedUp() {
		if (!this.duringRunning()) {
			return;
		}
		if (GU.notNullAndEmpty(
				this.taskId2Job)) {
			this.taskId2Job.entrySet()
			.stream()
			.forEach(entry -> {
				this.jobAssignedTask.stream()
				.filter(task -> {
					return task.getTaskId().contentEquals(entry.getKey())
							&& !task.getExecuteStatus().equals(entry.getValue().getExecuteStatus());
				})
				.findAny()
				.ifPresent(task -> {
					task.setExecuteStatus(
							entry.getValue().getExecuteStatus());
				});
			});
		}
	}

	public LocalDateTime jobCalculateTime(
			ScheduledTimeType scheduledTimeType) {
		LocalDateTime date = null;
		if (scheduledTimeType == ScheduledTimeType.CRON) {
			date = this.startedAt;
		} else if (scheduledTimeType == ScheduledTimeType.SECOND_DELAY) {
			date = this.completedAt;
		}
		return date == null ? 
				LocalDateTime.now() : date;
	}

	@Override
	public JobInstanceState clone() throws CloneNotSupportedException {
		JobInstanceState clone = (JobInstanceState) super.clone();
		clone.setJobAssignedTask(
				this.jobAssignedTask.stream()
				.map(task -> {
					try {
						return task.clone();
					} catch (Exception e) {
						log.error("error clone:{}-{}", task, e);
						return task;
					}
				})
				.collect(Collectors.toList()));
		return clone;
	}

}