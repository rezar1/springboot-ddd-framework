package com.zero.ddd.akka.ratelimiter.limiter;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2023-12-08 02:49:10
 * @Desc 些年若许,不负芳华.
 *
 */
public class SmoothBursty extends SmoothRateLimiter {

	/**
	 * The work (permits) of how many seconds can be saved up if this RateLimiter is
	 * unused?
	 */
	final double maxBurstSeconds;

	SmoothBursty(
			SleepingStopwatch stopwatch, 
			double maxBurstSeconds) {
		super(stopwatch);
		this.maxBurstSeconds = maxBurstSeconds;
	}

	@Override
	protected void doSetRate(
			double permitsPerSecond, 
			double stableIntervalMicros) {
		double oldMaxPermits = this.maxPermits;
		maxPermits = maxBurstSeconds * permitsPerSecond;
		if (oldMaxPermits == Double.POSITIVE_INFINITY) {
			// if we don't special-case this, we would get storedPermits == NaN, below
			storedPermits = maxPermits;
		} else {
			storedPermits = 
					// initial state 
					(oldMaxPermits == 0.0) ? 
							0.0 : storedPermits * maxPermits / oldMaxPermits;
		}
	}

	@Override
	long storedPermitsToWaitTime(
			double storedPermits, 
			double permitsToTake) {
		return 0L;
	}

	@Override
	double coolDownIntervalMicros() {
		return stableIntervalMicros;
	}
	
}