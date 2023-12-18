package com.zero.ddd.akka.ratelimiter.limiter;

import static java.lang.Math.min;
import static java.util.concurrent.TimeUnit.SECONDS;

import com.google.common.math.LongMath;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2023-12-15 05:26:12
 * @Desc 些年若许,不负芳华.
 *
 */
public class BucketRateLimiter extends VisitRateLimiter {
	
	private double maxBurstSeconds = 1.0d;
	
	/**
	 * The currently stored permits.
	 */
	protected double storedPermits;

	/**
	 * The maximum number of stored permits.
	 */
	protected double maxPermits;

	/**
	 * The interval between two unit requests, at our stable rate. E.g., a stable
	 * rate of 5 permits per second has a stable interval of 200ms.
	 */
	protected double stableIntervalMicros;

	/**
	 * The time when the next request (no matter its size) will be granted. After
	 * granting a request, this is pushed further in the future. Large requests push
	 * this further than small requests.
	 */
	protected long nextFreeTicketMicros = 0L; // could be either in the past or future
	
	BucketRateLimiter(
			RateLimiterState limiterState) {
		super(
				new ReteLimiterStopwatch(
						limiterState.getElapsedMicros()));
		this.storedPermits = limiterState.getStoredPermits();
		this.maxPermits = limiterState.getMaxPermits();
		this.nextFreeTicketMicros = 
				limiterState.getNextFreeTicketMicros();
		this.stableIntervalMicros = 
				stableIntervalMicros(
						limiterState.getPermitsPerSecond());
	}

	protected BucketRateLimiter(
			RateLimiterStopwatch stopwatch,
			double maxBurstSeconds) {
		super(stopwatch);
		this.maxBurstSeconds = maxBurstSeconds;
	}
	
	@Override
	protected final void doSetRate(
			double permitsPerSecond, 
			long nowMicros) {
		resync(nowMicros);
		double stableIntervalMicros = 
				stableIntervalMicros(permitsPerSecond);
		this.stableIntervalMicros = stableIntervalMicros;
		doSetRate(permitsPerSecond, stableIntervalMicros);
	}

	private double stableIntervalMicros(double permitsPerSecond) {
		double stableIntervalMicros = 
				SECONDS.toMicros(1L) / permitsPerSecond;
		return stableIntervalMicros;
	}

	protected void doSetRate(
			double permitsPerSecond, 
			double stableIntervalMicros) {
		double oldMaxPermits = this.maxPermits;
		maxPermits = maxBurstSeconds * permitsPerSecond;
		if (oldMaxPermits == Double.POSITIVE_INFINITY) {
			storedPermits = maxPermits;
		} else {
			storedPermits = 
					(oldMaxPermits == 0.0) ? 
							0.0 : storedPermits * maxPermits / oldMaxPermits;
		}
	}

	@Override
	final long queryEarliestAvailable(long nowMicros) {
		return nextFreeTicketMicros;
	}

	@Override
	final long reserveEarliestAvailable(
			int requiredPermits, 
			long nowMicros) {
		this.resync(nowMicros);
		long returnValue = nextFreeTicketMicros;
		double storedPermitsToSpend = 
				min(requiredPermits, this.storedPermits);
		double freshPermits = requiredPermits - storedPermitsToSpend;
		long waitMicros = 
				this.storedPermitsToWaitTime(
						this.storedPermits, 
						storedPermitsToSpend) 
				+ (long) (freshPermits * stableIntervalMicros);
		this.nextFreeTicketMicros = 
				LongMath.saturatedAdd(
						nextFreeTicketMicros,
						waitMicros);
		this.storedPermits -= storedPermitsToSpend;
		return returnValue;
	}

	long storedPermitsToWaitTime(
			double storedPermits,
			double permitsToTake) {
		return 0;
	}

	double coolDownIntervalMicros() {
		return stableIntervalMicros;
	}

	void resync(long nowMicros) {
		if (nowMicros > nextFreeTicketMicros) {
			double newPermits = 
					(nowMicros - nextFreeTicketMicros) / coolDownIntervalMicros();
			storedPermits = min(maxPermits, storedPermits + newPermits);
			nextFreeTicketMicros = nowMicros;
		}
	}

}