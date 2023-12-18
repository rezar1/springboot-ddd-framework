package com.zero.ddd.akka.ratelimiter.actor;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2023-12-08 04:57:45
 * @Desc 些年若许,不负芳华.
 *
 */
public class RateLimiterClientActor {
	
	
	
	// -------- 接口/命令类定义 --------
	public static interface RateLimiterClientCommand extends RateLimiterCommand {}
	
	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	static class TryAcquirePermitsResp implements RateLimiterClientCommand {
		
		public TryAcquirePermitsResp(
				long tryAcquireNeedSleepMicro) {
			this.trySucc = tryAcquireNeedSleepMicro >= 0;
			this.sleepMicro = tryAcquireNeedSleepMicro;
		}
		private boolean trySucc;
		private long sleepMicro;
	} 
	
	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	static class AcquirePermitsResp implements RateLimiterClientCommand {
		private long sleepMicro;
	} 

}