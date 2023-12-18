package com.zero.ddd.akka.ratelimiter.state;

import com.zero.ddd.akka.ratelimiter.limiter.RateLimiterState;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2023-12-08 02:44:57
 * @Desc 些年若许,不负芳华.
 *
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RateLimiterRunningState implements RateLimiterState {

	/**
	 * 已启动的微秒值
	 */
	private long elapsedMicros;

	/**
	 * The interval between two unit requests, at our stable rate. E.g., a stable
	 * rate of 5 permits per second has a stable interval of 200ms.
	 */
	private double permitsPerSecond;

	/**
	 * The currently stored permits.
	 */
	private double storedPermits;

	/**
	 * The maximum number of stored permits.
	 */
	private double maxPermits;

	/**
	 * The time when the next request (no matter its size) will be granted. After
	 * granting a request, this is pushed further in the future. Large requests push
	 * this further than small requests.
	 */
	private long nextFreeTicketMicros = 0L; // could be either in the past or future

}