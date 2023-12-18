package com.zero.ddd.akka.ratelimiter;

import java.util.concurrent.TimeUnit;

import org.junit.Test;

import com.google.common.base.Stopwatch;
import com.google.common.base.Ticker;
import com.zero.ddd.akka.ratelimiter.limiter.ReteLimiterStopwatch;

import lombok.extern.slf4j.Slf4j;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2023-12-08 07:15:38
 * @Desc 些年若许,不负芳华.
 *
 */
@Slf4j
public class StopwatchTest {
	
	@Test
	public void test() throws InterruptedException {
		ReteLimiterStopwatch watch1 = new ReteLimiterStopwatch();
		TimeUnit.SECONDS.sleep(2);
		long elapsed = 
				watch1.readMicros();
		log.info("elapsed watch1:{}", elapsed);
		ReteLimiterStopwatch watch2 = 
				new ReteLimiterStopwatch(elapsed);
		log.info("elapsed watch2:{}", watch2.readMicros());
	}
	
	@Test
	public void test1() {
		Stopwatch watch1 = Stopwatch.createStarted();
		long elapsed = 
				watch1.elapsed(TimeUnit.NANOSECONDS);
		log.info("elapsed watch1:{}", elapsed);
		Stopwatch watch2 = 
				Stopwatch.createStarted(
						new Ticker() {
							@Override
							public long read() {
								return elapsed;
							}
						});
		log.info("elapsed watch2:{}", watch2.elapsed(TimeUnit.NANOSECONDS));
	}

}