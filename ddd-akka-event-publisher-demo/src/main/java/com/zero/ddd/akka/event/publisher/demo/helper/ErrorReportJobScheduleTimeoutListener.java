package com.zero.ddd.akka.event.publisher.demo.helper;

import java.time.LocalDateTime;
import java.util.Set;

import com.zero.ddd.akka.cluster.job.definition.JobScheudleTimeoutListener;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2023-09-27 03:54:32
 * @Desc 些年若许,不负芳华.
 *
 */
public class ErrorReportJobScheduleTimeoutListener implements JobScheudleTimeoutListener {

	@Override
	public void onJobScheudleTimeout(
			String jobName,
			LocalDateTime startedAt, 
			Set<String> curOnlilneWorkeres,
			String assignedTaskInfoJson) {
		
	}

}