package com.zero.ddd.akka.ratelimiter.state.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2023-12-08 05:58:32
 * @Desc 些年若许,不负芳华.
 *
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RateLimiterConfig {
	
	private String rateLimiterName;
	private int permitsPerSecond;

}