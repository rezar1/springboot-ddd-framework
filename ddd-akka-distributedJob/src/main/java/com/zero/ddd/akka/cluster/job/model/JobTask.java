package com.zero.ddd.akka.cluster.job.model;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.zero.ddd.akka.cluster.job.model.vo.TaskExecuteStatus;
import com.zero.helper.LocalDateTimeSerializer;

import lombok.Data;
import lombok.ToString;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2023-04-17 11:18:19
 * @Desc 些年若许,不负芳华.
 *
 */
@Data
@ToString
public class JobTask implements Cloneable {
	
	private String taskId;
	private String parentTaskId;
	private String taskType;
	@JsonIgnore
	private byte[] taskBody;
	private String worker;
	@JsonSerialize(
			using = LocalDateTimeSerializer.class)
	private LocalDateTime startedAt;
	@JsonSerialize(
			using = LocalDateTimeSerializer.class)
	private LocalDateTime completedAt;
	private TaskExecuteStatus executeStatus;
	
	private String taskResult;
	
	public JobTask(
			String taskId, 
			String parentTaskId,
			String taskType,
			byte[] taskBody) {
		this.taskId = taskId;
		this.taskType = taskType;
		this.parentTaskId = parentTaskId;
		this.taskBody = taskBody;
		this.startedAt = LocalDateTime.now();
		this.executeStatus = 
				TaskExecuteStatus.WAIT_ASSIGN;
	}

	public void completeTask(
			String taskResult, 
			TaskExecuteStatus executeStatus) {
		this.taskResult = taskResult;
		this.executeStatus = executeStatus;
		this.completedAt = LocalDateTime.now();
	}

	public boolean notCompleted() {
		return this.executeStatus == TaskExecuteStatus.RUNNING
				|| this.executeStatus == TaskExecuteStatus.ASSIGNED;
	}
	
	public void taskStartRunning() {
		this.executeStatus = TaskExecuteStatus.RUNNING;
	}

	public void resetToWaitAssign() {
		this.worker = null;
		this.executeStatus = 
				TaskExecuteStatus.WAIT_ASSIGN;
	}

	public boolean waitAssign() {
		return this.executeStatus == TaskExecuteStatus.WAIT_ASSIGN;
	}

	public void assignTo(
			String worker) {
		this.worker = worker;
		this.executeStatus = 
				TaskExecuteStatus.ASSIGNED;
	}

	public void startRunning() {
		if (this.executeStatus 
				== TaskExecuteStatus.ASSIGNED) {
			this.executeStatus = 
					TaskExecuteStatus.RUNNING;
		}
	}

	public boolean isFail() {
		return this.executeStatus 
				== TaskExecuteStatus.FAIL;
	}

	public boolean justAssigned() {
		return this.executeStatus == TaskExecuteStatus.ASSIGNED;
	}

	@Override
	public JobTask clone() throws CloneNotSupportedException {
		return (JobTask) super.clone();
	}

}