package com.zero.ddd.core.toolsets.lock;

import java.time.Duration;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.zero.helper.GU;

import lombok.extern.slf4j.Slf4j;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2022-06-28 03:50:45
 * @Desc 些年若许,不负芳华.
 *
 *	需要分布式锁的方法增强
 *
 */
@Component
@Aspect
@Order(2)
@Slf4j
public class ClusterLockAspect {
	
	@Autowired
	private IProdiveClusterLock iProdiveClusterLock;
	
	@Pointcut("@annotation(com.zero.ddd.core.toolsets.lock.ClusterLockBusiness)")
    public void lockBusinessCut() {
		
    }
	
	/**
	 * @param pjp
	 * @return
	 * @throws Throwable
	 */
	@Around("lockBusinessCut() && @annotation(cut)")
	public Object doRequestMethod(
            ProceedingJoinPoint joinPoint,
            ClusterLockBusiness cut) throws Throwable {
		String lockCenter = cut.lockCenter();
		String lockBusiness = cut.lockBusiness();
		if (GU.isNullOrEmpty(lockBusiness)) {
			MethodSignature sign = 
					(MethodSignature)joinPoint.getSignature();
			lockBusiness = sign.getName();
		}
		ClusterReentryLock lock = 
				this.iProdiveClusterLock.lock(
						this.parseClusterLockAcquireParam(
								lockCenter, 
								lockBusiness,
								cut.lockHoldModel(),
								cut.lockHoldUnit()));
		boolean tryAcquire = false;
		try {
			tryAcquire = 
					lock.tryAcquire(
							cut.timeout() <= 0 ? 
									Duration.ofMillis(-1) : Duration.ofMillis(cut.unit().toMillis(cut.timeout())));
			if (log.isDebugEnabled()) {
				log.debug("for business:{} tryAcquire:{}", lockBusiness, tryAcquire);
			}
			if (tryAcquire) {
				return joinPoint.proceed();
			}
		} catch (InterruptedException ex) {
			log.warn("try acquire lock for business:{} been interrupted", lockBusiness);
		} finally {
			if (tryAcquire) {
				lock.release();
			}
		}
		return null;
	}

	private ClusterLockAcquireParam parseClusterLockAcquireParam(
			String lockCenter,
			String lockBusiness,
			LockHoldModel lockHoldModel,
			long lockHoldUnit) {
		return ClusterLockAcquireParam.builder()
				.lockCenter(lockCenter)
				.lockBusiness(lockBusiness)
				.holdModel(lockHoldModel)
				.lockHoldUnit(lockHoldUnit)
				.build();
	}
	

}

