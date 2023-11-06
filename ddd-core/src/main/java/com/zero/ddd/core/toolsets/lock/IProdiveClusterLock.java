package com.zero.ddd.core.toolsets.lock;

import java.time.Duration;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2022-07-19 10:51:16
 * @Desc 些年若许,不负芳华.
 *
 */
public interface IProdiveClusterLock {
	
	Duration NOT_TIMEOUT = null;
	String COMMON_LOCK_CENTER = "lock-common";
	
	public default ClusterReentryLock lock(String business) {
		return lock(
				ClusterLockAcquireParam.builder().lockBusiness(business).build());
	}
	
	public ClusterReentryLock lock(
			ClusterLockAcquireParam config);

}

