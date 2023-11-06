package com.zero.ddd.akka.cluster.core.initializer.cluster;

import org.springframework.beans.factory.annotation.Value;

import com.zero.ddd.akka.cluster.core.initializer.actor.IAsSpawnActor;
import com.zero.ddd.akka.cluster.core.operations.IProbjectUniqueId;

import akka.actor.Address;
import akka.actor.typed.javadsl.ActorContext;
import akka.cluster.typed.Cluster;
import lombok.extern.slf4j.Slf4j;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2022-06-30 11:01:37
 * @Desc 些年若许,不负芳华.
 *
 */
@Slf4j
public class AkkaClusterProbjectUniqueId implements IProbjectUniqueId, IAsSpawnActor {
	
	@Value("${spring.application.name}")
	private String serverName;
	
	private String uniqueId;

	@Override
	public String uniqueId() {
		return this.uniqueId;
	}

	@Override
	public void spawn(ActorContext<Void> context) {
		Address selfAddress = 
				Cluster.get(context.getSystem()).selfMember().address();
		String hostPort = selfAddress.hostPort();
		this.uniqueId = 
				serverName + "-" + hostPort.substring(hostPort.indexOf("@") + 1);
		log.info("probject uniqueId:{}", this.uniqueId);
	}

}

