package com.zero.ddd.akka.cluster.toolset.config;

import java.util.List;

import com.zero.ddd.akka.cluster.core.initializer.config.AkkaClusterProperties;
import com.zero.ddd.akka.cluster.core.initializer.config.AkkaOverrideConfig;
import com.zero.ddd.akka.cluster.core.initializer.config.IOverriderAkkaClusterConfig;
import com.zero.ddd.core.toolsets.lock.IProdiveClusterLock;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2022-06-30 09:34:38
 * @Desc 些年若许,不负芳华.
 *
 */
public class AkkaToolSetConfigOverride implements IOverriderAkkaClusterConfig {
	
	@Override
	public void doOverride(
			AkkaClusterProperties clusterConfig, 
			AkkaOverrideConfig overrideConfig) {
		List<String> roles = 
				overrideConfig.getValue("akka.cluster.roles");
		// 添加锁相关的基本角色
		roles.add(IProdiveClusterLock.COMMON_LOCK_CENTER);
		overrideConfig.appendOverride("akka.cluster.sharding.passivation.strategy", "none");
	}

}

