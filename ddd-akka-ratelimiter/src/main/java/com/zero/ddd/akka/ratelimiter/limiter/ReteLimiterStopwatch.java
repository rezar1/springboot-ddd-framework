package com.zero.ddd.akka.ratelimiter.limiter;

import static java.util.concurrent.TimeUnit.MICROSECONDS;

import com.google.common.base.Stopwatch;
import com.zero.ddd.akka.ratelimiter.limiter.VisitRateLimiter.RateLimiterStopwatch;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2023-12-08 07:31:09
 * @Desc 些年若许,不负芳华.
 *
 */
public class ReteLimiterStopwatch implements RateLimiterStopwatch {

	private final Stopwatch stopwatch = Stopwatch.createStarted();
	
	private long elapsed;
	
	public ReteLimiterStopwatch() {
		this(0);
	}
	
	public ReteLimiterStopwatch(
			long elapsed) {
		this.elapsed = elapsed;
	}

	@Override
	public long readMicros() {
		return elapsed + stopwatch.elapsed(MICROSECONDS);
	}
	
}