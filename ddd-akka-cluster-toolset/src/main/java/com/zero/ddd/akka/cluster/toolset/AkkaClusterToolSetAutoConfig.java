package com.zero.ddd.akka.cluster.toolset;

import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.zero.ddd.akka.cluster.core.AkkaClusterAutoConfig;
import com.zero.ddd.akka.cluster.toolset.config.AkkaToolSetConfigOverride;
import com.zero.ddd.akka.cluster.toolset.lock.impl.AkkaClusterLockProvider;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2022-07-26 05:04:36
 * @Desc 些年若许,不负芳华.
 *
 */
@Configuration
@AutoConfigureAfter(AkkaClusterAutoConfig.class)
public class AkkaClusterToolSetAutoConfig {

	@Bean
	public AkkaClusterLockProvider akkaClusterLockProvider() {
		return new AkkaClusterLockProvider();
	}
	
	@Bean
	public AkkaToolSetConfigOverride akkaToolSetConfigOverride() {
		return new AkkaToolSetConfigOverride();
	}
	
}