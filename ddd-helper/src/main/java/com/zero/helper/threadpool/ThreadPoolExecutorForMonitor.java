package com.zero.helper.threadpool;

import java.util.Arrays;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import io.micrometer.core.instrument.ImmutableTag;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Timer;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2021-12-21 07:52:50
 * @Desc 些年若许,不负芳华.
 *
 */
public class ThreadPoolExecutorForMonitor extends ThreadPoolExecutor {

	private static final RejectedExecutionHandler defaultHandler = new AbortPolicy();
	
	private Timer rpcExecutorMonitor;
	private AtomicInteger threadCoreSize;
	private AtomicInteger threadPoolSize;
	private AtomicInteger threadActiveCount;
	private AtomicLong threadTaskCount;
	private AtomicInteger threadLargestSize;
	private AtomicInteger threadMaxSize;

	public ThreadPoolExecutorForMonitor(
			String monitorName,
			int corePoolSize, 
			int maximumPoolSize, 
			long keepAliveTime, 
			TimeUnit unit,
			BlockingQueue<Runnable> workQueue,
			ThreadFactory factory) {
		super(
				corePoolSize, 
				maximumPoolSize, 
				keepAliveTime, 
				unit,
				(Metrics.gaugeCollectionSize(
						"monitor.executor.queue.size",
						Arrays.asList(
								new ImmutableTag(
										"monitorName", 
										monitorName),
								new ImmutableTag(
										"useing", 
										"binlog")),
						workQueue)),
				factory,
				defaultHandler);
		super.allowCoreThreadTimeOut(false);
		this.rpcExecutorMonitor = 
				Metrics.timer(
						"monitor.executor.thread",
						Arrays.asList(
								new ImmutableTag(
										"monitorName", 
										monitorName),
								new ImmutableTag(
										"useing", 
										"binlog")));
		this.threadCoreSize = 
				Metrics.gauge(
						"monitor.executor.core.size",
						Arrays.asList(
								new ImmutableTag(
										"monitorName", 
										monitorName),
								new ImmutableTag(
										"useing", 
										"binlog")),
						new AtomicInteger(0));
		// 曾经达到的最大的线程数量 <= maxSize
		this.threadLargestSize = 
				Metrics.gauge(
						"monitor.executor.largest.size",
						Arrays.asList(
								new ImmutableTag(
										"monitorName", 
										monitorName),
								new ImmutableTag(
										"useing", 
										"binlog")),
						new AtomicInteger(0));
		this.threadMaxSize = 
				Metrics.gauge(
						"monitor.executor.max.size",
						Arrays.asList(
								new ImmutableTag(
										"monitorName", 
										monitorName),
								new ImmutableTag(
										"useing", 
										"binlog")),
						new AtomicInteger(0));
		this.threadPoolSize = 
				Metrics.gauge(
						"monitor.executor.pool.size",
						Arrays.asList(
								new ImmutableTag(
										"monitorName", 
										monitorName),
								new ImmutableTag(
										"useing", 
										"binlog")),
						new AtomicInteger(0));
		this.threadActiveCount = 
				Metrics.gauge(
						"monitor.executor.active",
						Arrays.asList(
								new ImmutableTag(
										"monitorName", 
										monitorName),
								new ImmutableTag(
										"useing", 
										"binlog")),
						new AtomicInteger(0));
		this.threadTaskCount = 
				Metrics.gauge(
						"monitor.executor.task.count",
						Arrays.asList(
								new ImmutableTag(
										"monitorName", 
										monitorName),
								new ImmutableTag(
										"useing", 
										"binlog")),
						new AtomicLong(0));
		this.startMonitor();
	}
	
	private void startMonitor() {
		Executors.newSingleThreadScheduledExecutor()
		.scheduleWithFixedDelay(
				() -> {
					this.threadActiveCount.set(super.getActiveCount());
					this.threadCoreSize.set(super.getCorePoolSize());
					this.threadLargestSize.set(super.getLargestPoolSize());
					this.threadMaxSize.set(super.getMaximumPoolSize());
					this.threadPoolSize.set(super.getPoolSize());
					this.threadTaskCount.set(super.getTaskCount());
				}, 
				5,
				5,
				TimeUnit.SECONDS);
	}

	@Override
	public void execute(Runnable command) {
		super.execute(() -> {
			this.rpcExecutorMonitor.record(command);
		});
	}

	@Override
	public void shutdown() {
		super.shutdown();
	}

	@Override
	protected void terminated() {
		super.terminated();
	}

}
