package com.zero.ddd.akka.cluster.job.definition;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2023-04-20 08:04:14
 * @Desc 些年若许,不负芳华.
 *
 */
public interface CompleteTaskHandler {
	public void completed(
			String taskId, 
			String result, 
			boolean success);
}