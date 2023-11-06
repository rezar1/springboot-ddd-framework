package com.zero.ddd.akka.cluster.job.processor;

import java.time.Duration;
import java.util.concurrent.CompletionStage;

import com.zero.ddd.akka.cluster.job.actor.JobReplicatedCache.Cached;
import com.zero.ddd.akka.cluster.job.actor.JobReplicatedCache.GetFromCache;
import com.zero.ddd.akka.cluster.job.actor.JobReplicatedCache.JobCacheCommand;
import com.zero.ddd.akka.cluster.job.actor.JobReplicatedCache.PutInCache;
import com.zero.ddd.akka.cluster.job.model.JobDatabase;
import com.zero.ddd.akka.cluster.job.model.JobInstanceState;

import akka.Done;
import akka.actor.typed.ActorRef;
import akka.actor.typed.Scheduler;
import akka.actor.typed.javadsl.AskPattern;
import lombok.RequiredArgsConstructor;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2023-04-24 11:44:13
 * @Desc 些年若许,不负芳华.
 *
 */
@RequiredArgsConstructor
public class JobRelicatedCacheJobDatabase implements JobDatabase {
	
	private final ActorRef<JobCacheCommand> cacheRef;
	private final Scheduler scheduler;

	@Override
	public CompletionStage<JobInstanceState> loadJobInstance(
			String jobCacheName) {
		return 
				AskPattern.ask(
						this.cacheRef, 
						(ActorRef<Cached> resp) -> {
							return new GetFromCache(
									jobCacheKey(
											jobCacheName), 
									resp);
						},
						Duration.ofSeconds(5),
						this.scheduler)
				.thenApply(cached -> {
					JobInstanceState cachedJobState = cached.value.orElse(null);
					return cachedJobState;
				});
	}

	private String jobCacheKey(
			String jobCacheName) {
		return "JobState-Cache-" + jobCacheName;
	}

	@Override
	public CompletionStage<Done> updateJobInstance(
			String jobCacheName,
			JobInstanceState jobInstance) {
		return 
				AskPattern.ask(
						this.cacheRef, 
						(ActorRef<Done> resp) -> {
							return new PutInCache(
									jobCacheKey(
											jobCacheName), 
									jobInstance,
									resp);
						},
						Duration.ofSeconds(5),
						this.scheduler);
	}

}