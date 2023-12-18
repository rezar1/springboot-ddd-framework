package com.zero.ddd.akka.ratelimiter.limiter;

import static java.lang.Math.min;

import java.util.concurrent.TimeUnit;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2023-12-08 02:37:22
 * @Desc 些年若许,不负芳华.
 *
 */
public class SmoothWarmingUp extends SmoothRateLimiter {
	
	private final long warmupPeriodMicros;
	/**
	 * The slope of the line from the stable interval (when permits == 0), to the
	 * cold interval (when permits == maxPermits)
	 */
	private double slope;
	private double thresholdPermits;
	private double coldFactor;

	SmoothWarmingUp(
			SleepingStopwatch stopwatch, 
			long warmupPeriod, 
			TimeUnit timeUnit,
			double coldFactor) {
		super(stopwatch);
		this.warmupPeriodMicros = 
				timeUnit.toMicros(warmupPeriod);
		this.coldFactor = coldFactor;
	}

	@Override
	void doSetRate(
			double permitsPerSecond, 
			double stableIntervalMicros) {
		double oldMaxPermits = maxPermits;
		double coldIntervalMicros = stableIntervalMicros * coldFactor;
		thresholdPermits = 
				0.5 * warmupPeriodMicros / stableIntervalMicros;
		maxPermits = 
				thresholdPermits + 2.0 * warmupPeriodMicros / (stableIntervalMicros + coldIntervalMicros);
		slope = (coldIntervalMicros - stableIntervalMicros) / (maxPermits - thresholdPermits);
		if (oldMaxPermits == Double.POSITIVE_INFINITY) {
			// if we don't special-case this, we would get storedPermits == NaN, below
			storedPermits = 0.0;
		} else {
			storedPermits = (oldMaxPermits == 0.0) ? maxPermits // initial state is cold
					: storedPermits * maxPermits / oldMaxPermits;
		}
	}

	@Override
	long storedPermitsToWaitTime(
			double storedPermits, 
			double permitsToTake) {
		double availablePermitsAboveThreshold = storedPermits - thresholdPermits;
		long micros = 0;
		// measuring the integral on the right part of the function (the climbing line)
		if (availablePermitsAboveThreshold > 0.0) {
			double permitsAboveThresholdToTake = min(availablePermitsAboveThreshold, permitsToTake);
			// TODO(cpovirk): Figure out a good name for this variable.
			double length = 
					permitsToTime(availablePermitsAboveThreshold) + permitsToTime(availablePermitsAboveThreshold - permitsAboveThresholdToTake);
			micros = (long) (permitsAboveThresholdToTake * length / 2.0);
			permitsToTake -= permitsAboveThresholdToTake;
		}
		// measuring the integral on the left part of the function (the horizontal line)
		micros += (stableIntervalMicros * permitsToTake);
		return micros;
	}

	private double permitsToTime(double permits) {
		return stableIntervalMicros + permits * slope;
	}

	@Override
	double coolDownIntervalMicros() {
		return warmupPeriodMicros / maxPermits;
	}
}