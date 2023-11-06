package com.zero.ddd.akka.cluster.job.actor;

import java.util.Set;

import com.zero.ddd.akka.cluster.job.actor.JobScheduler.JobScheduledCommand;
import com.zero.ddd.akka.cluster.job.actor.JobSchedulerServiceWatcher.JobSchedulerServiceWatcherCommnad;
import com.zero.ddd.akka.cluster.job.actor.JobWorker.JobWorkerCommand;
import com.zero.ddd.akka.cluster.job.actor.JobWorker.JobWorkerState;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import akka.actor.typed.receptionist.Receptionist;
import akka.actor.typed.receptionist.Receptionist.Listing;
import akka.actor.typed.receptionist.ServiceKey;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2023-04-25 12:52:07
 * @Desc 些年若许,不负芳华.
 *
 */
@Slf4j(topic = "job")
public class JobSchedulerServiceWatcher extends AbstractBehavior<JobSchedulerServiceWatcherCommnad> {
	
	public static Behavior<JobSchedulerServiceWatcherCommnad> create(
			String jobName,
			String jobShowName,
			ActorRef<JobWorkerCommand> spawn) {
		return Behaviors.setup(context -> {
			return new JobSchedulerServiceWatcher(
					jobName,
					jobShowName,
					spawn, 
					context);
		});
	}
	
	private final String jobShowName;
	private final ActorRef<JobWorkerCommand> worker;
	private final ServiceKey<JobScheduledCommand> jobSchedulerServiceKey;
	
	public JobSchedulerServiceWatcher(
			String jobName,
			String jobShowName,
			ActorRef<JobWorkerCommand> worker,
			ActorContext<JobSchedulerServiceWatcherCommnad> context) {
		super(context);
		this.worker = worker;
		this.jobShowName = jobShowName;
		this.jobSchedulerServiceKey = 
				ServiceKeyHolder.jobSchedulerServiceKey(jobName);
		this.subscribeSchedulerService();
		this.worker.tell(JobWorkerState.PING);
	}

	private void subscribeSchedulerService() {
		ActorRef<Listing> messageAdapter = 
				super.getContext()
				.messageAdapter(
						Receptionist.Listing.class, 
						listing -> {
							return new TaskWorkerListingResponse(
									listing.getServiceInstances(
											this.jobSchedulerServiceKey));
						});
		super.getContext().getSystem()
		.receptionist()
		.tell(
				Receptionist.subscribe(
						this.jobSchedulerServiceKey, 
						messageAdapter));
	}

	@Override
	public Receive<JobSchedulerServiceWatcherCommnad> createReceive() {
		return super.newReceiveBuilder()
				.onMessage(
						TaskWorkerListingResponse.class, 
						services -> {
							if (services.isEmpty()) {
								log.info(
										"调度服务:[{}] 下线，发起ping请求完成任务调度分片重新启动",
										this.jobShowName);
								this.worker.tell(JobWorkerState.PING);
							}
							return this;
						})
				.build();
	}
	
	public static interface JobSchedulerServiceWatcherCommnad {


	}
	
	@ToString
	@RequiredArgsConstructor
	public static class TaskWorkerListingResponse implements JobSchedulerServiceWatcherCommnad {
		
		final Set<ActorRef<JobScheduledCommand>> serviceInstances;
		boolean isEmpty() {
			return this.serviceInstances.isEmpty();
		}
		
	}

}