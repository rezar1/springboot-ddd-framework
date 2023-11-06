package com.zero.ddd.akka.cluster.core.initializer;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.EventListener;

import com.typesafe.config.Config;
import com.zero.ddd.akka.cluster.core.helper.ClientAskRemoteExecutorConfig;
import com.zero.ddd.akka.cluster.core.initializer.actor.IAsSpawnActor;
import com.zero.ddd.akka.cluster.core.initializer.config.AkkaClusterProperties;
import com.zero.ddd.akka.cluster.core.initializer.config.AkkaOverrideConfig;
import com.zero.ddd.akka.cluster.core.initializer.config.IOverriderAkkaClusterConfig;
import com.zero.ddd.akka.cluster.core.initializer.event.AkkaClusterCommand;
import com.zero.ddd.akka.cluster.core.initializer.event.AkkaClusterLifecycleEvent;
import com.zero.helper.GU;

import akka.actor.typed.ActorSystem;
import akka.actor.typed.Behavior;
import akka.actor.typed.DispatcherSelector;
import akka.actor.typed.javadsl.Behaviors;
import lombok.extern.slf4j.Slf4j;
import scala.concurrent.ExecutionContextExecutor;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2022-06-29 04:17:44
 * @Desc 些年若许,不负芳华.
 *
 *	akka cluster 启动入口
 *	
 */
@Slf4j
public class AkkaClusterServerInitialization {
	
	@Autowired
	private AkkaClusterProperties clusterConfig;
	@Autowired
	private ApplicationContext context;
	
	private Semaphore startSemaphore = new Semaphore(1);
	private Semaphore stopSemaphore = new Semaphore(1);
	
	private ActorSystem<Void> system;
	
	@EventListener(AkkaClusterCommand.class)
	public void onLifecycleEvent(
			AkkaClusterCommand event) {
		if (event == AkkaClusterCommand.DO_START) {
			this.doStartAkkaCluster();
		} else if (event == AkkaClusterCommand.DO_STOP) {
			this.doStopAkkaCluster();
		}
	}
	
	private void doStopAkkaCluster() {
		if (!stopSemaphore.tryAcquire()) {
			return;
		}
		this.notifyClusterBeforeStop();
		this.system.terminate();
        try {
			this.system.getWhenTerminated()
			.toCompletableFuture()
			.whenComplete((done, error) -> {
				this.notifyClusterStoped();
				log.info(
						"Cluster [{}] Terminated, bye bye ~~~", 
						clusterConfig.getClusterName());
			
			})
			.join();
		} catch (Exception e) {
			log.error("error stop akka cluster:{}", e);
		}
	}
	
	private void doStartAkkaCluster() {
		if (!startSemaphore.tryAcquire()) {
			return;
		}
		// 存在dns配置，进行映射
		clusterConfig.initDnsMapper();
		AkkaOverrideConfig overrideConfig =
				new AkkaOverrideConfig();
		// 覆盖默认配置
		this.overrideConfig(overrideConfig);
		Config config = 
				overrideConfig.parseAllConfig(
        						this.clusterConfig.getAkkaConfigLoad());
		this.notifyClusterBeforeStart();
		this.system = 
				ActorSystem.create(
						RootBehavior.create(
								this.initActores()), 
						this.clusterConfig.getClusterName(),
						config);
		final ExecutionContextExecutor blockEx =
				system.dispatchers()
				.lookup(
						DispatcherSelector.fromConfig("blocking-io-dispatcher"));
		ClientAskRemoteExecutorConfig.configAskRemoteExecutor(blockEx);
		this.notifyClusterStarted();
		log.info("Cluster [{}] start successful - ^_^", clusterConfig.getClusterName());
	}
	
	private List<IAsSpawnActor> initActores() {
		Map<String, IAsSpawnActor> beansOfType = 
				this.context.getBeansOfType(
						IAsSpawnActor.class);
		return beansOfType
				.entrySet()
				.stream()
				.map(entry -> entry.getValue())
				.collect(Collectors.toList());
	}

	private void notifyClusterBeforeStart() {
		context.publishEvent(
				AkkaClusterLifecycleEvent.BEFORE_START);
	}
	
	private void notifyClusterBeforeStop() {
		context.publishEvent(
				AkkaClusterLifecycleEvent.BEFORE_STOP);
	}
	
	private void notifyClusterStarted() {
		context.publishEvent(
				AkkaClusterLifecycleEvent.STARTED);
	}
	
	private void notifyClusterStoped() {
		context.publishEvent(
				AkkaClusterLifecycleEvent.STOPED);
	}

	private static class RootBehavior {
		static Behavior<Void> create(
				List<IAsSpawnActor> initActores) {
			return Behaviors.setup(context -> {
				if (GU.notNullAndEmpty(initActores)) {
					initActores.stream()
					.forEach(as -> {
						as.spawn(context);
					});
				}
				return Behaviors.empty();
			});
		}
	}

	private Map<String, Object> overrideConfig(
			AkkaOverrideConfig overrideConfig) {
		Map<String, Object> sourceOverrideMap = new HashMap<>();
		this.iOverriderAkkaClusterConfiges()
		.stream()
		.sorted(
				Comparator.comparing(
						IOverriderAkkaClusterConfig::order))
		.forEach(override -> {
			override.doOverride(
					clusterConfig, 
					overrideConfig);
		});
		return sourceOverrideMap;
	}

	private Collection<IOverriderAkkaClusterConfig> iOverriderAkkaClusterConfiges() {
		return this.context.getBeansOfType(
				IOverriderAkkaClusterConfig.class)
				.entrySet()
				.stream()
				.map(entry -> entry.getValue())
				.collect(Collectors.toList());
	}

}