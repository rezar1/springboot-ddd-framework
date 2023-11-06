package com.zero.ddd.akka.cluster.core.initializer.actor;

import akka.actor.typed.javadsl.ActorContext;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2022-06-29 04:30:21
 * @Desc 些年若许,不负芳华.
 *
 */
public interface IAsSpawnActor {
	
	public void spawn(ActorContext<Void> context);

}

