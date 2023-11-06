package com.zero.ddd.akka.cluster.job;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.zero.ddd.akka.cluster.core.AkkaClusterAutoConfig;
import com.zero.ddd.akka.cluster.job.processor.DistributedJobEndpointRegister;
import com.zero.ddd.akka.cluster.job.processor.JobBeanProcessor;

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
public class AkkaClusterDistributedJobAutoConfig {

	@Bean
	public DistributedJobEndpointRegister register(
			@Value("${spring.application.name:}") String appName) {
		return new DistributedJobEndpointRegister(appName);
	}
	
	@Bean
	public JobBeanProcessor jobBeanProcessor(
			ApplicationContext context,
			DistributedJobEndpointRegister register) {
		return new JobBeanProcessor(context, register);
	}

}