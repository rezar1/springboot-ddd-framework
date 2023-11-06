package com.zero.ddd.akka.cluster.core.helper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2022-07-15 12:49:28
 * @Desc 些年若许,不负芳华.
 *
 */
public class SupportBusinessExecutor {
	
	public static void schedule(
			Runnable task, 
			long delay, 
			long fixedDelay,
			TimeUnit unit) {
		scheduleExecutor().scheduleWithFixedDelay(task, delay, delay, unit);
	}


	private static ScheduledExecutorService scheduleExecutor() {
		ScheduledExecutorService scheduleExecutor = 
				Executors.newSingleThreadScheduledExecutor(null);
		registeHook(scheduleExecutor);
		return scheduleExecutor;
	}


	private static ExecutorService registeHook(
			ExecutorService executor) {
		Thread shutdownHook = new Thread() {
            @Override
            public void run() {
                executor.shutdown();
            }
        };
        Runtime.getRuntime().addShutdownHook(shutdownHook);
        return executor;
	}

}

