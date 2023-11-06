package com.zero.ddd.akka.cluster.job.model.vo;

import java.util.Optional;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2023-04-27 02:44:46
 * @Desc 些年若许,不负芳华.
 *
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TaskIdAttachInfo {
	
	private int jobCircle;
	private int taskSequence;
	
	public static Optional<TaskIdAttachInfo> parseTaskId(
			String taskId) {
		if (taskId.contains("#")) {
			String[] split = taskId.split("#");
			TaskIdAttachInfo taskIdAttachInfo = 
					new TaskIdAttachInfo(
							Integer.parseInt(split[0]),
							Integer.parseInt(split[1]));
			return Optional.of(taskIdAttachInfo);
		}
		return Optional.empty();
	
		
	}

}