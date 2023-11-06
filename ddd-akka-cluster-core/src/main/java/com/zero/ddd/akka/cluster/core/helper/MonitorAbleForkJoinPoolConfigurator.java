package com.zero.ddd.akka.cluster.core.helper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinPool.ForkJoinWorkerThreadFactory;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

import com.typesafe.config.Config;
import com.zero.helper.threadpool.ForkJoinPoolForMonitor;

import akka.dispatch.DispatcherPrerequisites;
import akka.dispatch.ExecutorServiceConfigurator;
import akka.dispatch.ExecutorServiceFactory;
import akka.dispatch.MonitorableThreadFactory;
import lombok.extern.slf4j.Slf4j;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2021-12-21 07:37:48
 * @Desc 些年若许,不负芳华.
 *
 */
@Slf4j
public class MonitorAbleForkJoinPoolConfigurator extends ExecutorServiceConfigurator {
	
	private int parallelism;
	private boolean asyncModel;

	public MonitorAbleForkJoinPoolConfigurator(
			Config config, 
			DispatcherPrerequisites prerequisites) {
		super(config, prerequisites);
		if (!config.hasPath("fork-join-executor.parallelism")) {
			this.parallelism = 
					Runtime.getRuntime().availableProcessors() - 1;
		} else {
			this.parallelism =
					config.getInt("fork-join-executor.parallelism");
		}
		if (config.hasPath("fork-join-executor.async")) {
			this.asyncModel =
					config.getBoolean("fork-join-executor.async");
		}
	}

	@Override
	public ExecutorServiceFactory createExecutorServiceFactory(
			String dispatcherName, 
			ThreadFactory threadFactory) {
		String systemName = ((MonitorableThreadFactory) threadFactory).name();
		log.info("config dispatcher:{} parallelism:{} asyncModel:{}", dispatcherName, parallelism, asyncModel);
		AtomicInteger threadCount = new AtomicInteger(0);
		String nameFormat = "%s-%s-%s";
		ForkJoinWorkerThreadFactory factory = 
				new ForkJoinWorkerThreadFactory() {
					@Override
					public ForkJoinWorkerThread newThread(ForkJoinPool pool) {
						return new SelfForkJoinWorkerThread(
								pool,
								String.format(
										nameFormat, 
										systemName, 
										dispatcherName,
										threadCount.getAndIncrement()));
					}
				};
		return new ExecutorServiceFactory() {
			@Override
			public ExecutorService createExecutorService() {
				return new ForkJoinPoolForMonitor(
						dispatcherName, 
						parallelism,
						factory, 
						asyncModel);
			}
		};
	}
	
	public static class SelfForkJoinWorkerThread extends ForkJoinWorkerThread {

		protected SelfForkJoinWorkerThread(ForkJoinPool pool, String name) {
			super(pool);
			super.setName(name);
		}
		
	}


}

