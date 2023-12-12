package com.zero.ddd.akka.ratelimiter.limiter.guava;

import com.zero.ddd.akka.ratelimiter.state.RateLimiterRunningState;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2023-12-08 07:11:41
 * @Desc 些年若许,不负芳华.
 *
 */
public class RunningStateExposedSmoothBursty extends SmoothRateLimiter {
	
	public static RateLimiter create(
			RateLimiterRunningState state,
			double permitsPerSecond) {
		if (state == null) {
			return 
					new RunningStateExposedSmoothBursty(permitsPerSecond);
		} else {
			return 
					new RunningStateExposedSmoothBursty(state);
		}
	}
	
	private double maxBurstSeconds = 1.0d;
	
	public RunningStateExposedSmoothBursty(
			double permitsPerSecond) {
		super(new DefaultSleepingStopwatch());
		this.setRate(permitsPerSecond);
	}

	public RunningStateExposedSmoothBursty(
			RateLimiterRunningState state) {
		super(
				new DefaultSleepingStopwatch(
						state.getElapsedMicros()), 
				state.getStoredPermits(), 
				state.getMaxPermits(), 
				state.getStableIntervalMicros(), 
				state.getNextFreeTicketMicros());
	}
	
	public RateLimiterRunningState runningState() {
		return 
				new RateLimiterRunningState(
						stopwatch.readMicros(), 
						storedPermits,
						maxPermits, 
						stableIntervalMicros,
						nextFreeTicketMicros);
	}

	@Override
	long storedPermitsToWaitTime(
			double storedPermits,
			double permitsToTake) {
		return 0;
	}

	@Override
	double coolDownIntervalMicros() {
		return stableIntervalMicros;
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

}