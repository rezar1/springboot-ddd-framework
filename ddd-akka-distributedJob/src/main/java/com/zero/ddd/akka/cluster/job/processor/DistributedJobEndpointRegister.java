package com.zero.ddd.akka.cluster.job.processor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.zero.ddd.akka.cluster.core.initializer.actor.IAsSpawnActor;
import com.zero.ddd.akka.cluster.core.initializer.config.BlockingIODispatcherSelector;
import com.zero.ddd.akka.cluster.job.actor.JobReplicatedCache;
import com.zero.ddd.akka.cluster.job.actor.JobScheduler;
import com.zero.ddd.akka.cluster.job.actor.JobScheduler.JobScheduledCommand;
import com.zero.ddd.akka.cluster.job.actor.JobSchedulerServiceWatcher;
import com.zero.ddd.akka.cluster.job.actor.JobWorker;
import com.zero.ddd.akka.cluster.job.actor.JobWorker.JobWorkerCommand;
import com.zero.ddd.akka.cluster.job.definition.JobEndpoint;
import com.zero.ddd.akka.cluster.job.model.JobDatabase;
import com.zero.helper.GU;

import akka.actor.Address;
import akka.actor.typed.ActorRef;
import akka.actor.typed.Scheduler;
import akka.actor.typed.delivery.ConsumerController;
import akka.actor.typed.delivery.ConsumerController.SequencedMessage;
import akka.actor.typed.javadsl.ActorContext;
import akka.cluster.sharding.typed.ShardingEnvelope;
import akka.cluster.sharding.typed.delivery.ShardingConsumerController;
import akka.cluster.sharding.typed.delivery.ShardingProducerController;
import akka.cluster.sharding.typed.javadsl.ClusterSharding;
import akka.cluster.sharding.typed.javadsl.Entity;
import akka.cluster.sharding.typed.javadsl.EntityTypeKey;
import akka.cluster.typed.Cluster;
import akka.cluster.typed.ClusterSingleton;
import akka.cluster.typed.ClusterSingletonSettings;
import akka.cluster.typed.SingletonActor;
import lombok.var;
import lombok.extern.slf4j.Slf4j;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2023-04-21 07:34:46
 * @Desc 些年若许,不负芳华.
 *
 */
@Slf4j
public class DistributedJobEndpointRegister implements IAsSpawnActor {
	
	private String appName;
	private final List<JobEndpoint> allJobEndpointBuilder = new ArrayList<>();
	
	public DistributedJobEndpointRegister(
			String appName) {
		this.appName = appName;
	}

	@Override
	public void spawn(ActorContext<Void> context) {
		Scheduler scheduler = context.getSystem().scheduler();
		JobDatabase database = 
				new JobRelicatedCacheJobDatabase(
						context.spawn(
								JobReplicatedCache.create(), 
								"JobReplicatedCache",
								BlockingIODispatcherSelector.defaultDispatcher()), 
						scheduler);
		// 开启初始化相关Actor
		if (GU.notNullAndEmpty(this.allJobEndpointBuilder)) {
			var jobSchedulerRegion = 
					jobSchedulerRegion(context, database);
			Set<String> jobNames = new HashSet<>();
			this.allJobEndpointBuilder
			.stream()
			.forEach(endpoint -> {
				try {
					this.startJobEndpoint(jobSchedulerRegion, database, context, endpoint);
					jobNames.add(endpoint.getJobName());
				} catch (Exception e) {
					log.error("error:{}", e);
				}
			});
		}
	}

	private ActorRef<ShardingEnvelope<ConsumerController.SequencedMessage<JobScheduledCommand>>> jobSchedulerRegion(
			ActorContext<Void> context, 
			JobDatabase database) {
		// 根据并行度允许创建多个工作节点
		// 先创建任务调度分片Actor
		EntityTypeKey<ConsumerController.SequencedMessage<JobScheduledCommand>> entityTypeKey = 
				EntityTypeKey.create(
						ShardingConsumerController.entityTypeKeyClass(),
						"JobScheduler");
		Map<String, JobEndpoint> collect = 
				this.allJobEndpointBuilder.stream()
				.collect(
						Collectors.toMap(
								JobEndpoint::getJobName,
								endpoint -> endpoint));
		ActorRef<ShardingEnvelope<ConsumerController.SequencedMessage<JobScheduledCommand>>> jobSchedulerRegion = 
				ClusterSharding
				.get(context.getSystem())
				.init(
						Entity.of(
								entityTypeKey, 
								entityContext -> 
									ShardingConsumerController.create(
											start -> 
												JobScheduler.create(
														entityContext.getEntityId(),
														jobName -> collect.get(jobName),
														database, 
														start)))
						.withEntityProps(
								BlockingIODispatcherSelector.defaultDispatcher())
						.withRole(this.appName));
		return jobSchedulerRegion;
	}
	
	private void startJobEndpoint(
			ActorRef<ShardingEnvelope<SequencedMessage<JobScheduledCommand>>> jobSchedulerRegion, 
			JobDatabase database,
			ActorContext<Void> context, 
			JobEndpoint endpoint) {
		Address selfAddress = 
				Cluster.get(
						context.getSystem()).selfMember().address();
		String onleHostPort = 
				selfAddress.hostPort().replaceAll(".*?@", "");
		IntStream.range(
				0, 
				endpoint.getWorkerConcurrency())
		.forEach(index -> {
			String jobProducerId = 
					"producer-" + endpoint.getJobName() + "-" + onleHostPort + "-" + index;
			String workerId = 
					"worker-" + onleHostPort + "-" + UUID.randomUUID().toString().replace("-", "");
			ActorRef<ShardingProducerController.Command<JobScheduledCommand>> producerController = 
					context.spawn(
							ShardingProducerController.create(
									JobScheduledCommand.class,
									jobProducerId, 
									jobSchedulerRegion, 
									Optional.empty()),
							jobProducerId,
							BlockingIODispatcherSelector.defaultDispatcher());
			ActorRef<JobWorkerCommand> spawn = 
					context.spawn(
							JobWorker.create(
									endpoint,
									producerController),
							workerId,
							BlockingIODispatcherSelector.defaultDispatcher());
			if (index == 0) {
				this.startJobSchedulerServiceWatcher(
						endpoint.getJobName(),
						endpoint.jobShowName(), 
						spawn,
						context);
			}
		});
		
	}

	private void startJobSchedulerServiceWatcher(
			String jobName, 
			String jobShowName,
			ActorRef<JobWorkerCommand> spawn,
			ActorContext<Void> context) {
		ClusterSingleton.get(
				context.getSystem())
		.init(
				SingletonActor.of(
						JobSchedulerServiceWatcher.create(
								jobName, 
								jobShowName,
								spawn), 
						jobName + "-JobSchedulerServiceWatcher")
				.withProps(BlockingIODispatcherSelector.defaultDispatcher())
				.withSettings(
						ClusterSingletonSettings.create(
								context.getSystem())
						.withRole(appName)));
	}

	public void registe(
			JobEndpoint jobEndpoint) {
		if (this.existsSameJob(
				jobEndpoint.getJobName())) {
			throw new IllegalArgumentException(
					"重复的任务jobName:{" + jobEndpoint.getJobName() + "}");
		}
		this.allJobEndpointBuilder.add(jobEndpoint);
	}

	private boolean existsSameJob(String jobName) {
		return this.allJobEndpointBuilder.stream()
				.filter(end -> end.getJobName().contentEquals(jobName))
				.findAny()
				.isPresent();
	}

}