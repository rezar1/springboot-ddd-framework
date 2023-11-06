package com.zero.ddd.akka.cluster.toolset.lock.impl;

import java.time.Duration;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;

import com.google.common.cache.LoadingCache;
import com.zero.ddd.akka.cluster.core.initializer.actor.IAsSpawnActor;
import com.zero.ddd.akka.cluster.toolset.lock.LockGateActor;
import com.zero.ddd.akka.cluster.toolset.lock.LockGateActor.GateMessage;
import com.zero.ddd.akka.cluster.toolset.lock.LockGateActor.GateMessage.InitLockRef;
import com.zero.ddd.akka.cluster.toolset.lock.client.ClientLockActor.ClientLockMessage;
import com.zero.ddd.akka.cluster.toolset.lock.client.ClientLockActor.ClientLockMessage.InitLocalClusterLock;
import com.zero.ddd.akka.cluster.toolset.lock.client.InterProcessLock;
import com.zero.ddd.core.toolsets.lock.ClusterLockAcquireParam;
import com.zero.ddd.core.toolsets.lock.ClusterReentryLock;
import com.zero.ddd.core.toolsets.lock.IProdiveClusterLock;
import com.zero.helper.SimpleCacheBuilder;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Scheduler;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.AskPattern;
import lombok.extern.slf4j.Slf4j;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2022-07-19 10:52:44
 * @Desc 些年若许,不负芳华.
 * 
 * 	注册ClientLockActor
 *
 */
@Slf4j
public class AkkaClusterLockProvider implements IProdiveClusterLock, IAsSpawnActor {
	
	private CountDownLatch latch = new CountDownLatch(1);
	private LoadingCache<String, ActorRef<ClientLockMessage>> lockCenterCache;
	private LoadingCache<ClusterLockAcquireParam, ClusterReentryLock> clusterReenteryLockCache;
	private Scheduler scheduler;

	@Override
	public ClusterReentryLock lock(
			ClusterLockAcquireParam config) {
		if (this.lockCenterCache == null) {
			try {
				latch.await();
			} catch (InterruptedException e) {
				log.warn("take lock failure:{}", e);
				return null;
			}
		}
		try {
			return this.clusterReenteryLockCache.get(config);
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}
	
	@Override
	public void spawn(
			ActorContext<Void> context) {
		ActorRef<GateMessage> lockGateActor = 
				context.spawn(
						LockGateActor.gateBehavior(),
						"LockGateActor");
		this.scheduler = 
				context.getSystem().scheduler();
		this.lockCenterCache = 
				SimpleCacheBuilder.instance(
						lockCenter -> {
							CompletionStage<ActorRef<ClientLockMessage>> stage = 
									AskPattern.ask(
											lockGateActor, 
											ref -> {
												return new InitLockRef(
														lockCenter,
														ref);
											},
											Duration.ofSeconds(10),
											this.scheduler);
							try {
								return stage.toCompletableFuture().get();
							} catch (InterruptedException | ExecutionException e) {
								log.error("error init lock center:{}", lockCenter, e);
							}
							return null;
						});
		this.clusterReenteryLockCache = 
				SimpleCacheBuilder.instance(
						param -> {
							ActorRef<ClientLockMessage> lockRef = 
									this.lockCenterCache.getUnchecked(
											param.getLockCenter());
							CompletionStage<InterProcessLock> stage = 
									AskPattern.ask(
											lockRef, 
											ref -> {
												return new InitLocalClusterLock(
														param,
														ref);
											},
											Duration.ofSeconds(100),
											this.scheduler);
							try {
								return stage.toCompletableFuture().get();
							} catch (InterruptedException | ExecutionException e) {
								log.warn("take lock failure:{}", e);
								return null;
							}
						});
		latch.countDown();
	}

}

