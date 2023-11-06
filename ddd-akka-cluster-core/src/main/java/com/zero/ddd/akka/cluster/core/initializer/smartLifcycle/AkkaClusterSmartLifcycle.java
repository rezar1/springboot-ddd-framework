package com.zero.ddd.akka.cluster.core.initializer.smartLifcycle;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.SmartLifecycle;

import com.zero.ddd.akka.cluster.core.initializer.event.AkkaClusterCommand;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2022-07-11 03:04:27
 * @Desc 些年若许,不负芳华.
 *
 */
public class AkkaClusterSmartLifcycle implements SmartLifecycle {
	
	private volatile boolean running = false;
	
	@Autowired
	private ApplicationContext context;
	
	@Override
	public void start() {
		this.running = true;
		this.context.publishEvent(
				AkkaClusterCommand.DO_START);
	}

	@Override
	public void stop() {
		this.running = false;
		this.context.publishEvent(
				AkkaClusterCommand.DO_STOP);
	}

	@Override
	public int getPhase() {
		return Integer.MAX_VALUE;
	}

	@Override
	public boolean isRunning() {
		return running;
	}
}
