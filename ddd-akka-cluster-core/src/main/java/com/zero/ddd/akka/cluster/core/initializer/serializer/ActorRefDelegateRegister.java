package com.zero.ddd.akka.cluster.core.initializer.serializer;

import com.zero.ddd.akka.cluster.core.helper.ProtoBufSerializeUtils;
import com.zero.ddd.akka.cluster.core.initializer.actor.IAsSpawnActor;

import akka.actor.ExtendedActorSystem;
import akka.actor.typed.javadsl.ActorContext;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2022-08-19 06:14:54
 * @Desc 些年若许,不负芳华.
 *
 */
public class ActorRefDelegateRegister implements IAsSpawnActor {

	@Override
	public void spawn(ActorContext<Void> context) {
		ProtoBufSerializeUtils.registerDelegate(
				new ActorRefDelegate(
						(ExtendedActorSystem) context.getSystem().classicSystem()));
	}

}

