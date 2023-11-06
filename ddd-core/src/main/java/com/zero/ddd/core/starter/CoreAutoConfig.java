package com.zero.ddd.core.starter;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.zero.ddd.core.domainRegistry.DomainRegistry;
import com.zero.ddd.core.toolsets.lock.ClusterLockAspect;
import com.zero.ddd.core.toolsets.lock.IProdiveClusterLock;

import lombok.extern.slf4j.Slf4j;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2022-06-29 07:08:17
 * @Desc 些年若许,不负芳华.
 *
 */
@Slf4j
@Configuration
public class CoreAutoConfig {
	
	@Bean
	@ConditionalOnMissingBean(DomainRegistry.class)
	public DomainRegistry domainRegistry() {
		log.info("[DDD AutoConfig] DomainRegistry \t\tInit As:{}", DomainRegistry.class.getName());
		return new DomainRegistry();
	}

	@Bean
	@ConditionalOnBean(IProdiveClusterLock.class)
	public ClusterLockAspect clusterLockAspect() {
		log.info("[DDD AutoConfig] ClusterLockAspect \t\tInit As:{}", ClusterLockAspect.class.getName());
		return new ClusterLockAspect();
	}
	
}