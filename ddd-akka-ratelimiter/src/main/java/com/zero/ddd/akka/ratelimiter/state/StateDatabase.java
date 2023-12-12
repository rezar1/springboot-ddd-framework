package com.zero.ddd.akka.ratelimiter.state;

import java.util.concurrent.CompletionStage;

import akka.Done;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2023-04-19 02:13:45
 * @Desc 些年若许,不负芳华.
 *
 */
public interface StateDatabase {
	
	public CompletionStage<RateLimiterRunningState> loadRateLimiterRunningState(String rateLimiterName);
	
	public CompletionStage<Done> updateRateLimiterRunningState(String rateLimiterName, RateLimiterRunningState jobInstance);

}