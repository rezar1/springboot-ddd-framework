package com.zero.ddd.akka.cluster.toolset.lock.client;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.zero.ddd.core.toolsets.lock.ClusterLockAcquireParam;
import com.zero.ddd.core.toolsets.lock.ClusterReentryLock;
import com.zero.ddd.core.toolsets.lock.LockHoldModel.HoldValidition;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2022-07-19 10:48:18
 * @Desc 些年若许,不负芳华.
 * 
 *       进程为单元的分布式锁
 *
 */
@Slf4j
public class InterProcessLock implements ClusterReentryLock {

	private final Map<Thread, ClientLockThreadData> threadData = new ConcurrentHashMap<>();
	private volatile boolean duringLock = false;

	private ClusterLockAcquireParam param;
	private ReleaseBusinessLock releaseBusinessLock;
	private boolean exit;
	private final AtomicReference<VersionCountDownLatch> version = 
			new AtomicReference<>(VersionCountDownLatch.instanceNormal());
	
	private HoldValidition holdValidition;
	
	public InterProcessLock(
			ClusterLockAcquireParam param,
			ReleaseBusinessLock releaseBusinessLock) {
		this.param = param;
		this.holdValidition = 
				this.param.initHoldValidition();
		this.releaseBusinessLock = releaseBusinessLock;
	}
	
	private boolean awaitTimeout(long time, TimeUnit unit) throws InterruptedException {
		long timeout = unit.toMillis(time);
		long waitedTime = -1l;
		VersionCountDownLatch curVersion;
		do {
			if (waitedTime >= timeout) {
				return false;
			}
			long startTime = 
					System.currentTimeMillis();
			curVersion = this.version.get();
			// 如果超过等待事件还没获取到, 直接返回
			if (!curVersion.await(
					timeout - waitedTime, 
					TimeUnit.MILLISECONDS)) {
				return false;
			}
			if (exit) {
				return false;
			}
			waitedTime += System.currentTimeMillis() - startTime;
		} while (!Thread.currentThread().isInterrupted() && !this.resetVersion(curVersion));
		return true;
	}
	
	private boolean await() throws InterruptedException {
		VersionCountDownLatch curVersion;
		do {
			curVersion = this.version.get();
			curVersion.await();
			if (exit) {
				return false;
			}
		} while (!Thread.currentThread().isInterrupted() && !this.resetVersion(curVersion));
		return true;
	}
	
	private boolean resetVersion(
			VersionCountDownLatch curVersion) {
		VersionCountDownLatch newVersion = new VersionCountDownLatch();
		if (this.version.compareAndSet(
				curVersion, 
				newVersion)) {
			return true;
		}
		return false;
	}
	
	public boolean duringLock() {
		return this.duringLock;
	}
	
	public void clientServerExit() {
		this.exit = true;
		VersionCountDownLatch lockLatch = 
				this.version.get();
		lockLatch.countDown();
	}

	public void clientServerAcquiredLock() {
		if (this.duringLock) {
			log.warn(
					"business:[" + this.business() + "] during lock, why client acquire lock again ???");
			return;
		}
		this.processHoldLock();
		this.holdValidition.hold();
	}
	
	private void processHoldLock() {
		VersionCountDownLatch lockLatch = 
				this.version.get();
		if (lockLatch.getCount() <= 0) {
			log.warn(
					"business:[" + this.business() + "] lock count down multitimes");
			return;
		}
		lockLatch.countDown();
	}
	
	private void processContinueHoldLock() {
		this.processHoldLock();
	}

	private String business() {
		return this.param.getLockBusiness();
	}

	@Override
	public void acquire() throws InterruptedException {
		this.tryAcquire(null);
	}

	@Override
	public boolean tryAcquire(
			Duration waitTime) throws InterruptedException {
		Thread currentThread = Thread.currentThread();
		ClientLockThreadData clientLockThreadData = 
				this.threadData.get(currentThread);
		if (clientLockThreadData == null) {
			clientLockThreadData = 
					new ClientLockThreadData();
			this.threadData.put(
					currentThread, 
					clientLockThreadData);
		}
		boolean attemptLock = 
				clientLockThreadData.attemptLock(
						waitTime == null ? 0l : waitTime.toMillis(),
						TimeUnit.MILLISECONDS);
		if (attemptLock 
				&& !duringLock) {
			this.duringLock = true;
			if (log.isDebugEnabled()) {
				log.info(
						"thread lock success business:{}", 
						this.business());
			}
		}
		return attemptLock;
	}

	@Override
	public void release() {
		Thread currentThread = Thread.currentThread(); 
		ClientLockThreadData lockData = 
				threadData.get(currentThread);
		if (lockData == null) {
            throw new IllegalMonitorStateException(
            		"You do not own the lock: " + this.business());
        }
		if (lockData.release()) {
			if (this.currentProcessCanContinueHold()) {
				this.processContinueHoldLock();
				return;
			}
			try {
				this.duringLock = false;
				this.holdValidition.exitHold();
				this.releaseBusinessLock.release();
				if (log.isDebugEnabled()) {
					log.debug(
							"thread released lock for business:{}", 
							this.business());
				}
			} finally {
				threadData.remove(currentThread);
			}
		}
	}
	
	/**
	 * 判断当前进程是否需要继续持有该资源的锁
	 * 
	 * @return
	 */
	private boolean currentProcessCanContinueHold() {
		return holdValidition.canContinueHold();
	}

	private class ClientLockThreadData implements LockSempahore {

		private final AtomicInteger lockCount = new AtomicInteger(0);
		
		@Override
		public boolean attemptLock(
				long time, 
				TimeUnit unit) throws InterruptedException {
			if (this.lockCount.get() > 0) {
				this.lockCount.incrementAndGet();
				if (log.isDebugEnabled()) {
					log.debug(
							"thread reentry lock times:{} for business:{}", 
							this.lockCount.get(), 
							InterProcessLock.this.business());
				}
				return true;
			}
			boolean lockSucc = false;
			if (time <= 0l) {
				lockSucc = await();
			} else {
				lockSucc = awaitTimeout(time, unit);
			}
			if (lockSucc) {
				this.lockCount.set(1);
			}
			return lockSucc;
		}

		@Override
		public boolean release() {
			// 因为获取到的锁是可重入的，对lockCount进行减1，lockCount=0时才是真正释放锁
	        int newLockCount = 
	        		lockCount.decrementAndGet();
			if (newLockCount > 0) {
	            return false;
	        }
			if (newLockCount < 0) {
	            throw new IllegalMonitorStateException(
	            		"Lock count has gone negative for lock: " + InterProcessLock.this.business());
	        }
			return true;
		}
		
	}
	
	private static interface LockSempahore {
		boolean attemptLock(long time, TimeUnit unit) throws InterruptedException;
		boolean release();
	}
	
	@FunctionalInterface
	public static interface ReleaseBusinessLock {
		public void release();
	}
	
	@AllArgsConstructor
	@NoArgsConstructor
	static class VersionCountDownLatch {
		volatile CountDownLatch latch;
		
		public CountDownLatch latch() {
			if (this.latch == null) {
				synchronized(this) {
					if (this.latch == null) {
						this.latch = 
								new CountDownLatch(1);
					}
				}
			}
			return this.latch;
		}
		public void countDown() {
			this.latch().countDown();
		}
		public long getCount() {
			return this.latch().getCount();
		}
		public void await() throws InterruptedException {
			this.latch().await();
		}
		public boolean await(long milliseconds, TimeUnit timeUnit) throws InterruptedException {
			return this.latch().await(milliseconds, timeUnit);
		}
		public static VersionCountDownLatch instanceNormal() {
			return new VersionCountDownLatch(new CountDownLatch(1));
		}
	}
	
}
