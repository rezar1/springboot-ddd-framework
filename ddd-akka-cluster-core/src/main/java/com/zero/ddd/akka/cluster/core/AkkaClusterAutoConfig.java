package com.zero.ddd.akka.cluster.core;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.zero.ddd.akka.cluster.core.initializer.AkkaClusterServerInitialization;
import com.zero.ddd.akka.cluster.core.initializer.cluster.AkkaClusterProbjectUniqueId;
import com.zero.ddd.akka.cluster.core.initializer.config.AkkaClusterProperties;
import com.zero.ddd.akka.cluster.core.initializer.config.AkkaEventPublishConfigOverride;
import com.zero.ddd.akka.cluster.core.initializer.config.AkkaPersistenceConfigOverride;
import com.zero.ddd.akka.cluster.core.initializer.config.DefaultOverriderAkkaClusterConfig;
import com.zero.ddd.akka.cluster.core.initializer.config.IOverriderAkkaClusterConfig;
import com.zero.ddd.akka.cluster.core.initializer.serializer.ActorRefDelegateRegister;
import com.zero.ddd.akka.cluster.core.initializer.smartLifcycle.AkkaClusterSmartLifcycle;
import com.zero.ddd.akka.cluster.core.operations.IProbjectUniqueId;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2022-06-29 05:50:09
 * @Desc 些年若许,不负芳华.
 *
 */
@Configuration
public class AkkaClusterAutoConfig {
	
	@Bean
	@ConditionalOnMissingBean
	public IProbjectUniqueId akkaClusterProbjectUniqueId() {
		return new AkkaClusterProbjectUniqueId();
	}
	
	@Bean
	public ActorRefDelegateRegister actorRefDelegateRegister() {
		return new ActorRefDelegateRegister();
	}
	
	@Bean
	public AkkaClusterProperties akkaClusterProperties() {
		return new AkkaClusterProperties();
	}

	@Bean
	public AkkaClusterSmartLifcycle akkaClusterSmartLifcycle() {
		return new AkkaClusterSmartLifcycle();
	}
	
	@Bean
	public AkkaClusterServerInitialization akkaClusterServerInitialization() {
		return new AkkaClusterServerInitialization();
	}

	@Bean
	public IOverriderAkkaClusterConfig defaultOverriderAkkaClusterConfig() {
		return new DefaultOverriderAkkaClusterConfig();
	}
	
	@Bean
	public AkkaEventPublishConfigOverride akkaEventPublishConfigOverride() {
		return new AkkaEventPublishConfigOverride();
	}
	
	@Bean
	@ConditionalOnProperty(prefix = "spring", name = "datasource.url")
	public AkkaPersistenceConfigOverride akkaPersistenceConfigOverride() {
		return new AkkaPersistenceConfigOverride();
	}
	
}
