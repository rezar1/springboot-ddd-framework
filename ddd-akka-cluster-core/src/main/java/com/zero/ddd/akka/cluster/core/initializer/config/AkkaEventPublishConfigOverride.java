package com.zero.ddd.akka.cluster.core.initializer.config;

import java.util.Arrays;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2022-06-30 09:34:38
 * @Desc 些年若许,不负芳华.
 *
 */
public class AkkaEventPublishConfigOverride implements IOverriderAkkaClusterConfig {
	
	@Override
	public void doOverride(
			AkkaClusterProperties clusterConfig, 
			AkkaOverrideConfig overrideConfig) {
		overrideConfig.appendOverride(
				"akka.cluster.distributed-data.durable.keys", 
				Arrays.asList("durable-*"));
	}

}

