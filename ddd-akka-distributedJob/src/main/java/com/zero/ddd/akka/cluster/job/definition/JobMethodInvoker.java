package com.zero.ddd.akka.cluster.job.definition;


/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2023-04-23 07:35:25
 * @Desc 些年若许,不负芳华.
 *
 */
public interface JobMethodInvoker {
	
	public TaskResult invokeJobMethod(JobTaskContext context);

}