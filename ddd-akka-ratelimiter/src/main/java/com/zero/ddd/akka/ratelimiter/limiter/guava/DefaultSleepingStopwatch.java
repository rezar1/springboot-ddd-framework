package com.zero.ddd.akka.ratelimiter.limiter.guava;

import static java.util.concurrent.TimeUnit.MICROSECONDS;

import com.google.common.base.Stopwatch;
import com.google.common.util.concurrent.Uninterruptibles;
import com.zero.ddd.akka.ratelimiter.limiter.guava.RateLimiter.SleepingStopwatch;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2023-12-08 07:31:09
 * @Desc 些年若许,不负芳华.
 *
 */
public class DefaultSleepingStopwatch extends SleepingStopwatch {
	
	private long elapsed;
	final Stopwatch stopwatch = Stopwatch.createStarted();
	
	public DefaultSleepingStopwatch() {
		this(0);
	}
	
	public DefaultSleepingStopwatch(
			long elapsed) {
		this.elapsed = elapsed;
	}

	@Override
	public long readMicros() {
		return elapsed + stopwatch.elapsed(MICROSECONDS);
	}
	@Override
	protected void sleepMicrosUninterruptibly(long micros) {
		if (micros > 0) {
			Uninterruptibles.sleepUninterruptibly(micros, MICROSECONDS);
		}
	}


}