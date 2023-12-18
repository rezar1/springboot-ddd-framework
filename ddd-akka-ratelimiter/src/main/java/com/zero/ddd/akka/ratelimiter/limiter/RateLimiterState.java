package com.zero.ddd.akka.ratelimiter.limiter;


/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2023-12-15 05:46:17
 * @Desc 些年若许,不负芳华.
 *
 */
public interface RateLimiterState {
	
	public double getPermitsPerSecond();
	
	public double getStoredPermits();
	public double getMaxPermits();
	public long getNextFreeTicketMicros();
	
	public long getElapsedMicros();
	
}