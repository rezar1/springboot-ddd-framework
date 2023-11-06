package com.zero.ddd.akka.cluster.toolset.lock;

import com.zero.ddd.akka.cluster.toolset.lock.client.ClientLockActor.ClientLockMessage;
import com.zero.ddd.akka.cluster.toolset.lock.server.ServerLockActor.LockCommand;

import akka.actor.typed.receptionist.ServiceKey;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2022-07-22 07:53:22
 * @Desc 些年若许,不负芳华.
 *
 */
public class ServiceKeyFactory {
	
	public static ServiceKey<LockCommand> lockServerServiceKey(
			String lockCenter) {
		return ServiceKey.create(
				LockCommand.class, 
				"ClusterLockServer-" + lockCenter);
	}

	public static ServiceKey<ClientLockMessage> lockClientServiceKey(
			String lockCenter) {
		return ServiceKey.create(
				ClientLockMessage.class, 
				"ClusterLockClient-" + lockCenter);
	}

}

