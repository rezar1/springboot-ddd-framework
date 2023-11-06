package com.zero.ddd.akka.cluster.core.helper;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import lombok.extern.slf4j.Slf4j;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2021-12-20 12:57:39
 * @Desc 些年若许,不负芳华.
 *
 */
@Slf4j
public class ClientAskRemoteExecutorConfig {
	
	private static volatile Executor askRemoteExecutor;
	
	public static void configAskRemoteExecutor(
			int minThread, 
			int maxThread,
			int queueSize,
			long keepAliveTimeMill) {
		AtomicInteger counter = new AtomicInteger(0);
		configAskRemoteExecutor(
				new ThreadPoolExecutor(
						minThread, 
						maxThread,
						keepAliveTimeMill,
		                TimeUnit.MILLISECONDS,
		                queueSize <= 0 ? new LinkedBlockingQueue<Runnable>() : new ArrayBlockingQueue<Runnable>(queueSize),
		                run -> new Thread(run, "RpcAskRemoteThread-" + counter.getAndIncrement())));
	}
	
	public static void configAskRemoteExecutor(
			Executor askRemoteExecutor) {
		ClientAskRemoteExecutorConfig.askRemoteExecutor = askRemoteExecutor;
		log.info("askRemoteExecutor config with:{}", askRemoteExecutor);
	}
	
	public static Executor askRemoteExecutor() {
		return askRemoteExecutor == null ? ForkJoinPool.commonPool() : askRemoteExecutor;
	}

}

