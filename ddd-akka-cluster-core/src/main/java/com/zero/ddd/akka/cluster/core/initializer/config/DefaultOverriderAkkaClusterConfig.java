package com.zero.ddd.akka.cluster.core.initializer.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

import com.zero.ddd.akka.cluster.core.helper.ServerIpUtil;
import com.zero.helper.GU;

import lombok.var;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2022-06-29 05:29:43
 * @Desc 些年若许,不负芳华.
 *
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
public class DefaultOverriderAkkaClusterConfig implements IOverriderAkkaClusterConfig {
	
	private static final String DEFAULT_ROLE = "clusterMember";
	
	@Value("${spring.application.name:}")
	private String appName;

	@Override
	public void doOverride(AkkaClusterProperties clusterConfig, AkkaOverrideConfig overrideConfig) {
        Map<String, Object> overrides = 
        		overrideConfig.getOverrideMap();
        List<String> roles = new ArrayList<>();
        if (clusterConfig.hasRoles()) {
        	roles.addAll(
        			clusterConfig.roles());
        }
        if (this.appName != null) {
        	roles.add(this.appName);
        }
        roles.add(DEFAULT_ROLE);
        if (!roles.isEmpty()) {
        	overrides.put("akka.cluster.roles", roles);
        }
        String serverHostName = 
        		clusterConfig.getServerHostName();
        String ip = 
    			clusterConfig.isUseLocalHost() ? 
    					ServerIpUtil.LOCAL_SERVER_IP : ServerIpUtil.SERVER_IP;
        String hostname = null;
        if (GU.notNullAndEmpty(serverHostName)) {
        	hostname = 
        			this.tryReplaceHostNameMacro(
        					serverHostName);
            overrides.put(
            		"akka.remote.artery.canonical.hostname", 
            		serverHostName);
        } else {
        	hostname = clusterConfig.findDnsShowHostName(ip);
        }
        overrides.put(
        		"akka.remote.artery.canonical.hostname",
        		hostname);
        if (clusterConfig.getPort() != null) {
            overrides.put("akka.remote.artery.canonical.port", clusterConfig.getPort());
        }
        if (clusterConfig.isBindLocalIp()) {
        	overrides.put("akka.remote.artery.bind.hostname", "0.0.0.0");
        	overrides.put("akka.remote.artery.bind.port", clusterConfig.getPort());
        }
        
        if (GU.notNullAndEmpty(
        		clusterConfig.getSeedNodes())) {
        	overrides.put(
        			"akka.cluster.seed-nodes", 
        			clusterConfig.formatSeedNodes());
        } else {
        	overrides.put(
        			"akka.cluster.seed-nodes", 
        			clusterConfig.selfAsSeedNodes(
        					hostname));
        }
        
        overrides.put(
        		"akka.cluster.failure-detector.acceptable-heartbeat-pause", 
        		clusterConfig.acceptableHeartbeatPause());
        var defaultForkExecutorConfig = 
        		clusterConfig.defaultForkExecutorConfig();
		overrides.put(
				"akka.actor.default-dispatcher.fork-join-executor.parallelism", 
				defaultForkExecutorConfig.getParallelism());
		
		var actorExecutorConfig = 
				clusterConfig.actorExecutorConfig();
		overrides.put(
				"blocking-io-dispatcher.thread-pool-executor.core-size", 
				actorExecutorConfig.getCoreSize());
		overrides.put(
				"blocking-io-dispatcher.thread-pool-executor.max-size", 
				actorExecutorConfig.getMaxSize());
		overrides.put(
				"monitor.thread-pool-executor.keep-alive-time", 
				actorExecutorConfig.getKeepAliveTime());
		overrides.put(
				"blocking-io-dispatcher.thread-pool-executor.queue-size", 
				actorExecutorConfig.getQueueSize());
		overrides.put("akka.cluster.downing-provider-class", "akka.cluster.sbr.SplitBrainResolverProvider");
		overrides.put("akka.cluster.split-brain-resolver.keep-majority.role", DEFAULT_ROLE);
		overrides.put("akka.cluster.split-brain-resolver.down-all-when-unstable", "off");
		overrides.put("akka.cluster.split-brain-resolver.stable-after", "8s");
		overrides.put("akka.cluster.split-brain-resolver.active-strategy", "keep-majority");
        overrides.put("akka.jvm-exit-on-fatal-error", "false");
        overrides.put("akka.cluster.jmx.multi-mbeans-in-same-jvm", "on");
        overrides.put("akka.cluster.configuration-compatibility-check.enforce-on-join", "off");
	}

	private String tryReplaceHostNameMacro(
			String serverHostName) {
		if (serverHostName.contains("${HOST_NAME}")) {
			return 
					serverHostName.replace(
							"${HOST_NAME}", 
							ServerIpUtil.SERVER_HOST_NAME);
		}
		return serverHostName;
	}

}