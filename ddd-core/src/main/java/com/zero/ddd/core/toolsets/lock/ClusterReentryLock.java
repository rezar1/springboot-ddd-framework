package com.zero.ddd.core.toolsets.lock;

import java.time.Duration;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2022-07-19 10:46:50
 * @Desc 些年若许,不负芳华.
 *
 */
public interface ClusterReentryLock {
	
	public void acquire() throws InterruptedException;
	
	public boolean tryAcquire(Duration waitTime) throws InterruptedException;
	
	public void release();

}

