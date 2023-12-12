package com.zero.ddd.akka.ratelimiter.actor;

import com.zero.ddd.akka.ratelimiter.actor.RateLimiterServerActor.RateLimiterServerCommand;

import akka.actor.typed.receptionist.ServiceKey;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2023-04-20 08:26:18
 * @Desc 些年若许,不负芳华.
 *
 */
public class ServiceKeyHolder {
	
	public static ServiceKey<RateLimiterServerCommand> rateLimiterServerKey(
			String reateLimiterName) {
		return
				ServiceKey.create(
						RateLimiterServerCommand.class, 
						"SK-reateLimiter-" + reateLimiterName);
	}

}