package com.zero.ddd.akka.ratelimiter.limiter;

import static java.lang.Math.min;
import static java.util.concurrent.TimeUnit.SECONDS;

import com.google.common.math.LongMath;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2023-12-08 02:38:32
 * @Desc 些年若许,不负芳华.
 *
 */
public abstract class SmoothRateLimiter extends RateLimiter {

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
	
	SmoothRateLimiter(
			SleepingStopwatch stopwatch, 
			double storedPermits, 
			double maxPermits,
			double stableIntervalMicros, 
			long nextFreeTicketMicros) {
		super(stopwatch);
		this.storedPermits = storedPermits;
		this.maxPermits = maxPermits;
		this.stableIntervalMicros = stableIntervalMicros;
		this.nextFreeTicketMicros = nextFreeTicketMicros;
	}

	protected SmoothRateLimiter(
			SleepingStopwatch stopwatch) {
		super(stopwatch);
	}

	@Override
	protected final void doSetRate(
			double permitsPerSecond, 
			long nowMicros) {
		resync(nowMicros);
		double stableIntervalMicros = SECONDS.toMicros(1L) / permitsPerSecond;
		this.stableIntervalMicros = stableIntervalMicros;
		doSetRate(permitsPerSecond, stableIntervalMicros);
	}

	abstract void doSetRate(double permitsPerSecond, double stableIntervalMicros);

	@Override
	final double doGetRate() {
		return SECONDS.toMicros(1L) / stableIntervalMicros;
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
				storedPermitsToWaitTime(this.storedPermits, storedPermitsToSpend) + (long) (freshPermits * stableIntervalMicros);
		this.nextFreeTicketMicros = 
				LongMath.saturatedAdd(
						nextFreeTicketMicros,
						waitMicros);
		this.storedPermits -= storedPermitsToSpend;
		return returnValue;
	}

	/**
	 * Translates a specified portion of our currently stored permits which we want
	 * to spend/acquire, into a throttling time. Conceptually, this evaluates the
	 * integral of the underlying function we use, for the range of [(storedPermits
	 * - permitsToTake), storedPermits].
	 *
	 * <p>
	 * This always holds: {@code 0 <= permitsToTake <= storedPermits}
	 */
	abstract long storedPermitsToWaitTime(double storedPermits, double permitsToTake);

	/**
	 * Returns the number of microseconds during cool down that we have to wait to
	 * get a new permit.
	 */
	abstract double coolDownIntervalMicros();

	/**
	 * Updates {@code storedPermits} and {@code nextFreeTicketMicros} based on the
	 * current time.
	 */
	void resync(long nowMicros) {
		// if nextFreeTicket is in the past, resync to now
		if (nowMicros > nextFreeTicketMicros) {
			double newPermits = 
					(nowMicros - nextFreeTicketMicros) / coolDownIntervalMicros();
			storedPermits = min(maxPermits, storedPermits + newPermits);
			nextFreeTicketMicros = nowMicros;
		}
	}
	
}