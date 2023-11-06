package com.zero.ddd.akka.cluster.core.helper;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.typesafe.config.Config;
import com.zero.helper.threadpool.ThreadPoolExecutorForMonitor;

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
public class MonitorAbleExecutorServiceConfigurator extends ExecutorServiceConfigurator {

	private int coreSize;
	private int maxSize;
	private int keepAliveTime;
	private BlockingQueue<Runnable> queue;

	public MonitorAbleExecutorServiceConfigurator(
			Config config, 
			DispatcherPrerequisites prerequisites) {
		super(config, prerequisites);
		this.coreSize = 
				config.getInt("thread-pool-executor.core-size");
		this.maxSize = 
				config.getInt("thread-pool-executor.max-size");
		this.keepAliveTime = 
				config.getInt("thread-pool-executor.keep-alive-time");
		if (config.hasPath("thread-pool-executor.queue-size")) {
			this.queue = 
					new LinkedBlockingQueue<Runnable>();
		} else {
			int queueSize = 
					config.getInt("thread-pool-executor.queue-size");
			if (queueSize <= 0) {
				this.queue = 
						new LinkedBlockingQueue<Runnable>();
			} else {
				this.queue = 
						new ArrayBlockingQueue<Runnable>(queueSize);
			}
		}
	}

	@Override
	public ExecutorServiceFactory createExecutorServiceFactory(
			String dispatcherName, 
			ThreadFactory threadFactory) {
		String systemName = ((MonitorableThreadFactory) threadFactory).name();
		log.info("config dispatcher:{} systemName:{}", dispatcherName, systemName);
		AtomicInteger threadCount = new AtomicInteger(0);
		String nameFormat = "%s-%s-%s";
		return new ExecutorServiceFactory() {
			@Override
			public ExecutorService createExecutorService() {
				return new ThreadPoolExecutorForMonitor(
						dispatcherName,
						coreSize,
						maxSize,
						keepAliveTime,
						TimeUnit.SECONDS,
						queue,
						run -> new Thread(
								run, 
								String.format(
										nameFormat, 
										systemName, 
										dispatcherName,
										threadCount.getAndIncrement())));
			}
		};
	}

}

