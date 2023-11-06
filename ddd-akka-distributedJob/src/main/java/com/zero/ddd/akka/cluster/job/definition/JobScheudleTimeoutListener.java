package com.zero.ddd.akka.cluster.job.definition;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2023-04-25 02:41:37
 * @Desc 些年若许,不负芳华.
 *
 */
public interface JobScheudleTimeoutListener {
	
	public void onJobScheudleTimeout(
			String jobName,
			LocalDateTime startedAt, 
			Set<String> curOnlilneWorkeres,
			String assignedTaskInfoJson);

}