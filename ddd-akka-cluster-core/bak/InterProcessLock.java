package com.zero.ddd.akka.cluster.core.toolset.lock.client;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.zero.ddd.akka.cluster.core.toolset.lock.AkkaClusterLock;

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
public class InterProcessLock implements AkkaClusterLock {

	private final Map<Thread, ClientLockThreadData> threadData = new ConcurrentHashMap<>();
	private volatile boolean duringLock = false;

	private String business;
	private ReleaseBusinessLock releaseBusinessLock;
	private final AtomicInteger version = new AtomicInteger(0);
	private volatile CountDownLatch lockLatch = new CountDownLatch(1);
	
	public InterProcessLock(
			String business,
			ReleaseBusinessLock releaseBusinessLock) {
		this.business = business;
		this.releaseBusinessLock = releaseBusinessLock;
	}
	
	private boolean awaitTimeout(long time, TimeUnit unit) throws InterruptedException {
		long timeout = unit.toMillis(time);
		long waitedTime = -1l;
		int curVersion;
		do {
			if (waitedTime >= timeout) {
				return false;
			}
			long startTime = 
					System.currentTimeMillis();
			curVersion = this.version.get();
			// 如果超过等待事件还没获取到, 直接返回
			if (!this.lockLatch.await(
					timeout - waitedTime, 
					TimeUnit.MILLISECONDS)) {
				return false;
			}
			waitedTime += System.currentTimeMillis() - startTime;
		} while (!Thread.currentThread().isInterrupted() && !this.resetVersion(curVersion));
		return true;
	}
	
	private boolean await() throws InterruptedException {
		int curVersion;
		do {
			curVersion = this.version.get();
			this.lockLatch.await();
		} while (!Thread.currentThread().isInterrupted() && !this.resetVersion(curVersion));
		return true;
	}
	
	private synchronized boolean resetVersion(
			int curVersion) {
		if (this.version.compareAndSet(
				curVersion, 
				curVersion + 1)) {
			this.lockLatch = new CountDownLatch(1);
			return true;
		}
		return false;
	}
	
	public boolean duringLock() {
		return this.duringLock;
	}

	public void clientServerAcquiredLock() {
		if (this.duringLock) {
			throw new IllegalStateException(
					"business:[" + this.business + "] during lock, why client acquire lock again ???");
		}
		if (this.lockLatch.getCount() <= 0) {
			throw new IllegalStateException(
					"business:[" + this.business + "] lock count down multitimes");
		}
		this.lockLatch.countDown();
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
						this.business);
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
            		"You do not own the lock: " + this.business);
        }
		if (lockData.release()) {
			try {
				this.duringLock = false;
				this.releaseBusinessLock.release();
				if (log.isDebugEnabled()) {
					log.debug(
							"thread released lock for business:{}", 
							this.business);
				}
			} finally {
				threadData.remove(currentThread);
			}
		}
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
							InterProcessLock.this.business);
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
	            		"Lock count has gone negative for lock: " + InterProcessLock.this.business);
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

}
