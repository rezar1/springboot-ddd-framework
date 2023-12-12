package com.zero.ddd.akka.ratelimiter;

import org.junit.Test;

import com.zero.ddd.akka.ratelimiter.limiter.guava.RateLimiter;


/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2023-12-08 11:19:32
 * @Desc 些年若许,不负芳华.
 *
 */
public class GuavaRatelimiterTest {
	
	@Test
	public void test() {
		RateLimiter limiter = 
				RateLimiter.create(5);
		System.out.println("get 200 tokens: " + limiter.acquire(200) + "s");
		System.out.println("get 1 tokens: " + limiter.acquire(1) + "s");
	}
	
	@Test
	public void testSmoothBursty3() {
		RateLimiter r = RateLimiter.create(5);
		 while (true)
		 {
			r.tryAcquire(5);
		    System.out.println("get 5 tokens: " + r.acquire(5) + "s");
		    System.out.println("get 1 tokens: " + r.acquire(1) + "s");
		    System.out.println("get 1 tokens: " + r.acquire(1) + "s");
		    System.out.println("get 1 tokens: " + r.acquire(1) + "s");
		    System.out.println("end");
		 /**
		       * output:
		       * get 5 tokens: 0.0s
		       * get 1 tokens: 0.996766s 滞后效应，需要替前一个请求进行等待
		       * get 1 tokens: 0.194007s
		       * get 1 tokens: 0.196267s
		       * end
		       * get 5 tokens: 0.195756s
		       * get 1 tokens: 0.995625s 滞后效应，需要替前一个请求进行等待
		       * get 1 tokens: 0.194603s
		       * get 1 tokens: 0.196866s
		       */
		 }
	}

}