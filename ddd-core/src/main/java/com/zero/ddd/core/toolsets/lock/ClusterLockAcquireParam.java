package com.zero.ddd.core.toolsets.lock;

import com.zero.ddd.core.toolsets.lock.LockHoldModel.HoldValidition;
import com.zero.helper.GU;

import lombok.Builder;
import lombok.Builder.Default;
import lombok.EqualsAndHashCode;
import lombok.Getter;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2022-08-01 08:02:37
 * @Desc 些年若许,不负芳华.
 *
 */
@Getter
@Builder
@EqualsAndHashCode(of = { "lockCenter", "lockBusiness" })
public class ClusterLockAcquireParam {
	
	@Default
	private String lockCenter = IProdiveClusterLock.COMMON_LOCK_CENTER;
	private String lockBusiness;
	private LockHoldModel holdModel;
	private long lockHoldUnit;
	
	public String getLockCenter() {
		return GU.notNullAndEmpty(this.lockCenter) ? 
				this.lockCenter.trim() : IProdiveClusterLock.COMMON_LOCK_CENTER;
	}
	
	public HoldValidition initHoldValidition() {
		return LockHoldModel.init(
				this.getHoldModel(), 
				this.lockHoldUnit);
	}
	
}

