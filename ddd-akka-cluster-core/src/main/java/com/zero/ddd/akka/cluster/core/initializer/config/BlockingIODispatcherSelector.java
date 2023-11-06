package com.zero.ddd.akka.cluster.core.initializer.config;

import akka.actor.typed.DispatcherSelector;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2022-08-01 10:44:37
 * @Desc 些年若许,不负芳华.
 *
 */
public class BlockingIODispatcherSelector {
	
	public static DispatcherSelector defaultDispatcher() {
		return DispatcherSelector.fromConfig("blocking-io-dispatcher");
	}

}