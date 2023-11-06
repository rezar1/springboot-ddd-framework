package com.zero.ddd.akka.cluster.job.definition;

import java.time.Duration;
import java.time.LocalDateTime;

import com.zero.ddd.akka.cluster.core.helper.ProtoBufSerializeUtils;
import com.zero.ddd.akka.cluster.job.constants.Constants;
import com.zero.ddd.akka.cluster.job.model.vo.ShardingRequest;
import com.zero.helper.GU;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2023-04-10 11:25:08
 * @Desc 些年若许,不负芳华.
 *
 */
@RequiredArgsConstructor
public class JobTaskContext {

	@Getter
	private final String taskId;
	@Getter
	private final String taskType;
	private final byte[] task;
	@Getter
	private final LocalDateTime startedAt;
	
	public ShardingRequest shardingRequest() {
		return this.task(ShardingRequest.class);
	}
	
	public <T> T task(Class<T> targetClass) {
		return GU.isNullOrEmpty(task) ? 
				null : ProtoBufSerializeUtils.deserialize(task, targetClass);
	}
	
	public boolean isRootTask() {
		return this.taskType.contentEquals(
				Constants.ROOT_TASK_TYPE);
	}
	
	public boolean isTaskOfType(String taskType) {
		return this.taskType.contentEquals(taskType);
	}
	
	public boolean taskIsExpired(
			Duration expired) {
		return Duration.between(
				this.startedAt,
				LocalDateTime.now())
				.abs()
				.compareTo(expired) >= 0;
	}
	
}