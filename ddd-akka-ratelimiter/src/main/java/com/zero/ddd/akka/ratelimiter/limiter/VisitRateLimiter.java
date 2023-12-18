package com.zero.ddd.akka.ratelimiter.limiter;

import static com.google.common.base.Preconditions.checkArgument;
import static java.lang.Math.max;
import static java.util.concurrent.TimeUnit.MICROSECONDS;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import lombok.AllArgsConstructor;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2023-12-15 05:32:17
 * @Desc 些年若许,不负芳华.
 *
 */
@AllArgsConstructor
public abstract class VisitRateLimiter {
	
	protected final RateLimiterStopwatch stopwatch;
	
	public static VisitRateLimiter create(
			double permitsPerSecond) {
		return create(
				new ReteLimiterStopwatch(), 
				permitsPerSecond);
	}
	
	public static VisitRateLimiter create(
			RateLimiterState state) {
		return 
				new BucketRateLimiter(
						state);
	}
	
	static VisitRateLimiter create(
			RateLimiterStopwatch stopwatch, 
			double permitsPerSecond) {
		VisitRateLimiter rateLimiter = 
				new BucketRateLimiter(
						stopwatch,
						1.0 /* maxBurstSeconds */);
		rateLimiter.setRate(permitsPerSecond);
		return rateLimiter;
	}
	
	public final void setRate(
			double permitsPerSecond) {
		checkArgument(
				permitsPerSecond > 0.0 && !Double.isNaN(permitsPerSecond), "rate must be positive");
		doSetRate(permitsPerSecond, stopwatch.readMicros());
	}
	
	abstract void doSetRate(double permitsPerSecond, long nowMicros);
	
	public long acquire() {
		return acquire(1);
	}

	public long acquire(int permits) {
		return this.reserve(permits);
	}

	final long reserve(int permits) {
		checkPermits(permits);
		return 
				this.reserveAndGetWaitLength(
						permits, 
						stopwatch.readMicros());
	}

	public long tryAcquire(
			long timeout, 
			TimeUnit unit) {
		return tryAcquire(
				1,
				timeout, 
				unit);
	}

	public long tryAcquire(int permits) {
		return tryAcquire(permits, 0, MICROSECONDS);
	}

	public long tryAcquire() {
		return tryAcquire(1, 0, MICROSECONDS);
	}

	public long tryAcquire(
			int permits,
			Duration timeout) {
		if (timeout == null) {
			return this.tryAcquire(permits);
		}
		return 
				this.tryAcquire(
						permits, 
						timeout.toMillis(), 
						TimeUnit.MILLISECONDS);
	}

	public long tryAcquire(
			int permits, 
			long timeout, 
			TimeUnit unit) {
		long timeoutMicros = 
				max(unit.toMicros(timeout), 0);
		checkPermits(permits);
		long microsToWait;
		long nowMicros = stopwatch.readMicros();
		if (!canAcquire(nowMicros, timeoutMicros)) {
			return -1;
		} else {
			microsToWait = 
					this.reserveAndGetWaitLength(
							permits,
							nowMicros);
		}
		return microsToWait;
	}

	private boolean canAcquire(
			long nowMicros, 
			long timeoutMicros) {
		return 
				this.queryEarliestAvailable(nowMicros) - timeoutMicros <= nowMicros;
	}

	final long reserveAndGetWaitLength(
			int permits,
			long nowMicros) {
		long momentAvailable = 
				this.reserveEarliestAvailable(permits, nowMicros);
		return max(momentAvailable - nowMicros, 0);
	}
	
	abstract long queryEarliestAvailable(long nowMicros);

	abstract long reserveEarliestAvailable(int permits, long nowMicros);
	
	static interface RateLimiterStopwatch {

		public long readMicros();

	}
	
	private static void checkPermits(int permits) {
		checkArgument(permits > 0, "Requested permits (%s) must be positive", permits);
	}
	
}