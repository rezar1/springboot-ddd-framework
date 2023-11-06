package com.zero.ddd.akka.cluster.job.definition;

import java.util.List;

import com.zero.helper.GU;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2023-04-24 07:34:16
 * @Desc 些年若许,不负芳华.
 *
 */
@Getter
@RequiredArgsConstructor
public class TaskResult {
	
	private final boolean succ;
	private final String result;
	private final MapedTask mapTask;
	
	public boolean isMapAnswer() {
		return this.mapTask != null;
	}
	
	public static TaskResult map(
			String taskType, 
			List<? extends Object> tasks) {
		if (GU.isNullOrEmpty(tasks)) {
			return succ();
		}
		return new TaskResult(
				true, 
				null, 
				new MapedTask(
						taskType, 
						tasks));
	}
	
	public static TaskResult succ() {
		return new TaskResult(true, null, null);
	}
	
	public static TaskResult succ(String result) {
		return new TaskResult(true, result, null);
	}
	
	public static TaskResult fail() {
		return new TaskResult(false, null, null);
	}
	
	public static TaskResult fail(String result) {
		return new TaskResult(false, result, null);
	}
	
	@Getter
	@RequiredArgsConstructor
	public static class MapedTask {
		private final String taskType;
		private final List<? extends Object> mapTasks;
	}
	
}