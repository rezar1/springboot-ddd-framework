package com.zero.ddd.akka.cluster.core.initializer.config;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2022-06-29 05:28:55
 * @Desc 些年若许,不负芳华.
 *
 */
public interface IOverriderAkkaClusterConfig {
	
	public default int order() {
		return 0;
	}

	public void doOverride(
			AkkaClusterProperties clusterConfig, 
			AkkaOverrideConfig overrideConfig) ;

}

