package com.zero.ddd.akka.cluster.job.actor;

import com.zero.ddd.akka.cluster.job.actor.JobScheduler.JobScheduledCommand;
import com.zero.ddd.akka.cluster.job.actor.JobWorker.JobWorkerCommand;

import akka.actor.typed.receptionist.ServiceKey;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2023-04-20 08:26:18
 * @Desc 些年若许,不负芳华.
 *
 */
public class ServiceKeyHolder {
	
	public static ServiceKey<JobWorkerCommand> jobWorkerServiceKey(
			String jobName) {
		return
				ServiceKey.create(
						JobWorkerCommand.class, 
						"SK-Worker-" + jobName);
	}
	
	public static ServiceKey<JobScheduledCommand> jobSchedulerServiceKey(
			String jobName) {
		return
				ServiceKey.create(
						JobScheduledCommand.class, 
						"SK-Scheduler-" + jobName);
	}

}