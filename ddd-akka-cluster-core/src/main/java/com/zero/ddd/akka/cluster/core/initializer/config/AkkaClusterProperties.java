package com.zero.ddd.akka.cluster.core.initializer.config;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.SocketUtils;

import com.zero.ddd.akka.cluster.core.helper.DnsMapper;
import com.zero.ddd.akka.cluster.core.helper.SupportBusinessExecutor;
import com.zero.helper.GU;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2022-06-29 04:50:20
 * @Desc 些年若许,不负芳华.
 *
 */
@Slf4j
@Data
@Component
@ConfigurationProperties(prefix = "akka")
public class AkkaClusterProperties {
	
	private String akkaConfigLoad = "baseCluster";

	private String serverHostName;
	private boolean useLocalHost;
	private boolean bindLocalIp = true;
	private Integer port;
	private String[] roles;
	private String[] seedNodes;
	private String clusterName = "defaultAkkaCluster";
	private long invokeMethodTimeoutOfMill = 30 * 1000;
	private int acceptableHeartbeatPause = 30;
	private DnsConfig dnsConfig; 
	// 配置业务使用的调度器
	private DispatcherExecutorConfig actorExecutorConfig;
    // 配置默认调度器
	private DispatcherForkJoinExecutorConfig defaultForkExecutorConfig;
	// 公网出口ip获取类
	private String ipAcquisitionClass;
	
	public Integer getPort() {
		if (this.port == null) {
			this.port = 
					SocketUtils.findAvailableTcpPort();
			log.info("Akka Cluster Use available port:{}", this.port);
		}
		return this.port;
	}
	
	public List<String> formatSeedNodes() {
		if (GU.isNullOrEmpty(seedNodes)) {
			seedNodes = new String[] {};
		}
        return Arrays.asList(seedNodes)
        		.stream()
        		.map(this::formatAkkaSystemUrl)
        		.collect(Collectors.toList());
    }
	
	public List<String> selfAsSeedNodes(String hostname) {
		return Arrays.asList(hostname + ":" + this.port)
				.stream()
        		.map(this::formatAkkaSystemUrl)
        		.collect(Collectors.toList());
	}
    
    public Map<String, String> getDnsMapper() {
    	return this.dnsConfig != null ? this.dnsConfig.getDnsMapper() : Collections.emptyMap();
    }
    

    private String formatAkkaSystemUrl(String hostAndPort) {
        if (hostAndPort.startsWith("akka://")) {
            return hostAndPort;
        }
        return String.format("akka://%s@%s", this.clusterName, hostAndPort);
    }
    
    public String acceptableHeartbeatPause() {
    	return this.acceptableHeartbeatPause + "s";
    }
	
	/**
     * 线程数量范围 [corePoolSizeMin, min(corePoolSizeMax, Runtime.getRuntime().availableProcessors() * corePoolSizeFactory)]
     * 
     * @say little Boy, don't be sad.
     * @name Rezar
     * @time 2021-12-19 11:08:01
     * @Desc 些年若许,不负芳华.
     *
     */
    @Data
    public static class DispatcherForkJoinExecutorConfig {
		private int parallelism = 20;
    }
    
    /**
     * 线程数量范围 [corePoolSizeMin, min(corePoolSizeMax, Runtime.getRuntime().availableProcessors() * corePoolSizeFactory)]
     * 
     * @say little Boy, don't be sad.
     * @name Rezar
     * @time 2021-12-19 11:07:04
     * @Desc 些年若许,不负芳华.
     * 
     * 用于:
     * ①:调度客户端ClientActor + 运行客户端等待服务端返回的Future
     * ②:调度服务端Actor + 执行实际服务端业务逻辑
     *
     */
    @Data
    public static class DispatcherExecutorConfig {
    	private int coreSize = 30;
    	private int maxSize = 160;
    	private int keepAliveTime = 300;
    	private int queueSize = -1;
    }

	public void initDnsMapper() {
		if (this.dnsConfig != null) {
	        DnsMapper.configDns(
	        		this.dnsConfig.getDnsMapper());
	        if (this.dnsConfig.isAutoRefresh()) {
	        	SupportBusinessExecutor.schedule(
	        			() -> {
	        				DnsMapper.configDns(
	        						this.dnsConfig.refresh());
	        			},
	        			6,
	        			6, 
	        			TimeUnit.MINUTES);
	        }
		}
	}

	public boolean hasRoles() {
		return GU.notNullAndEmpty(this.roles);
	}

	public Collection<? extends String> roles() {
		return Arrays.asList(this.roles);
	}

	public String findDnsShowHostName(String ip) {
		if (this.dnsConfig == null) {
			return ip;
		}
		return this.dnsConfig.getShowHostName(ip);
	}
	
	public DispatcherForkJoinExecutorConfig defaultForkExecutorConfig() {
		return this.defaultForkExecutorConfig == null ? new DispatcherForkJoinExecutorConfig() : this.defaultForkExecutorConfig;
	}

	public DispatcherExecutorConfig actorExecutorConfig() {
		return this.actorExecutorConfig == null ? new DispatcherExecutorConfig() : this.actorExecutorConfig;
	}

}