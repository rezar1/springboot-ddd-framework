package com.zero.helper.threadpool;

import java.util.Arrays;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
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
 * @time 2021-12-21 09:54:28
 * @Desc 些年若许,不负芳华.
 *
 */
public class ForkJoinPoolForMonitor extends ForkJoinPool {
	
	private Timer executorMonitor;
	private AtomicInteger threadParallelism;
	private AtomicInteger threadPoolSize;
	private AtomicInteger threadActiveCount;
	private AtomicLong queueSubmissionCount;
	private AtomicLong queueTaskCount;
	private AtomicInteger runingThreadCount;

	public ForkJoinPoolForMonitor(
			String monitorName,
			int parallelism,
            ForkJoinWorkerThreadFactory factory,
            boolean asyncMode) {
		super(
				parallelism,
				factory,
				null,
				asyncMode);
		this.executorMonitor = 
				Metrics.timer(
						"monitor.forkjoin.thread",
						Arrays.asList(
								new ImmutableTag(
										"monitorName", 
										monitorName),
								new ImmutableTag(
										"useing", 
										"binlog")));
		this.threadParallelism = 
				Metrics.gauge(
						"monitor.forkjoin.parallelism",
						Arrays.asList(
								new ImmutableTag(
										"monitorName", 
										monitorName),
								new ImmutableTag(
										"useing", 
										"binlog")),
						new AtomicInteger(0));
		this.queueTaskCount = 
				Metrics.gauge(
						"monitor.forkjoin.task.count",
						Arrays.asList(
								new ImmutableTag(
										"monitorName", 
										monitorName),
								new ImmutableTag(
										"useing", 
										"binlog")),
						new AtomicLong(0));
		this.runingThreadCount = 
				Metrics.gauge(
						"monitor.forkjoin.running.count",
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
						"monitor.forkjoin.pool.size",
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
						"monitor.forkjoin.active",
						Arrays.asList(
								new ImmutableTag(
										"monitorName", 
										monitorName),
								new ImmutableTag(
										"useing", 
										"binlog")),
						new AtomicInteger(0));
		this.queueSubmissionCount = 
				Metrics.gauge(
						"monitor.forkjoin.queue.submission.count",
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
					this.threadActiveCount.set(super.getActiveThreadCount());
					this.threadParallelism.set(super.getPoolSize());
					this.threadPoolSize.set(super.getPoolSize());
					this.runingThreadCount.set(super.getRunningThreadCount());
					this.queueSubmissionCount.set(super.getQueuedSubmissionCount());
					this.queueTaskCount.set(super.getQueuedTaskCount());
				}, 
				5,
				5,
				TimeUnit.SECONDS);
	}

	/**
	 * 	getPoolSize():当前线程池内工作线程的数量
		getParallelism():并行级别
		getActiveThreadCount():正在执行任务的线程数
		getRunningThreadCount():正在运行的（没有被阻塞的）工作线程数
		getQueuedSubmissionCount():提交到线程池中，但尚未开始执行的任务数量
		getQueuedTaskCount():提交到线程池中，已经开始执行任务的数量
		getStealCount():工作线程队列从另外一个工作线程队列中窃取的全部任务数量。
	 */
	@Override
	public void execute(Runnable task) {
		super.execute(() -> {
			this.executorMonitor.record(task);
		});
	}

}

