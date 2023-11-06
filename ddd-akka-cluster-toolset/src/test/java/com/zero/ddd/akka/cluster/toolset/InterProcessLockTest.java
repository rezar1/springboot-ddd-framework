package com.zero.ddd.akka.cluster.toolset;

import static org.junit.Assert.assertTrue;

import java.time.Duration;
import java.util.BitSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import org.apache.commons.lang3.RandomUtils;
import org.junit.Test;

import com.zero.ddd.akka.cluster.toolset.lock.client.InterProcessLock;
import com.zero.ddd.core.toolsets.lock.ClusterLockAcquireParam;

import lombok.extern.slf4j.Slf4j;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2022-07-25 07:12:46
 * @Desc 些年若许,不负芳华.
 *
 */
@Slf4j
public class InterProcessLockTest {
	
	ClusterLockAcquireParam param = ClusterLockAcquireParam.builder().lockBusiness("Test-Lock-0").build();
	
	
	@Test
	public void testSum() throws InterruptedException {
		Semaphore semphore = new Semaphore(1);
		InterProcessLock lock = 
				new InterProcessLock(
						param,
						() -> {
//							log.info("Thread:{} released lock", Thread.currentThread().getName());
							semphore.release();
						});
		int size = 10000;
		CountDownLatch latch = new CountDownLatch(size);
		ExecutorService newFixedThreadPool = 
				Executors.newFixedThreadPool(300);
		BitSet bitSet = new BitSet();
		for (int i = 0 ;i < size;i ++) {
			int index = i;
			newFixedThreadPool.execute(() -> {
				try {
					lock.acquire();
					bitSet.set(index);
					if (RandomUtils.nextInt(0, 100) <= 5) {
						TimeUnit.MILLISECONDS.sleep(3);
					}
					lock.release();
					latch.countDown();
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			});
		}
		new Thread(
				() -> {
					while(latch.getCount() > 0l) {
						try {
							semphore.acquire();
							lock.clientServerAcquiredLock();
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
					}
				}).start();
		latch.await();
		assertTrue(bitSet.cardinality() == size);
	}
	
	@Test
	public void testWaitTimeout() throws InterruptedException {
		InterProcessLock lock = 
				new InterProcessLock(
						param,
						() -> {
							log.info("Thread:{} released lock", Thread.currentThread().getName());
						});
		Duration timeout = Duration.ofSeconds(5);
		long timeStart = System.currentTimeMillis();
		boolean tryAcquire = 
				lock.tryAcquire(timeout);
		assertTrue((System.currentTimeMillis() - timeStart) >= timeout.toMillis());
		assertTrue(!tryAcquire);
	}
	
	@Test
	public void testInterProcessLock() throws InterruptedException {
		CountDownLatch latch = new CountDownLatch(5);
		InterProcessLock lock = 
				new InterProcessLock(
						param,
						() -> {
							log.info("Thread:{} released lock", Thread.currentThread().getName());
						});
		Runnable target = 
				() -> {
					try {
						lock.acquire();
						lock.acquire();
						lock.acquire();
						log.info("Thread:{} acquired lock", Thread.currentThread().getName());
						latch.countDown();
						lock.release();
						lock.release();
						lock.release();
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				};
		int threadNum = 5;
		AtomicInteger threadIndex = new AtomicInteger(0);
		ExecutorService newFixedThreadPool = 
				Executors.newFixedThreadPool(
						threadNum, 
						run -> new Thread(
								run, "t:" + threadIndex.getAndIncrement()));
		IntStream.range(0, threadNum)
		.forEach(index -> {
			newFixedThreadPool.execute(target);
		});
		lock.clientServerAcquiredLock();
		TimeUnit.SECONDS.sleep(1);
		lock.clientServerAcquiredLock();
		TimeUnit.SECONDS.sleep(2);
		lock.clientServerAcquiredLock();
		TimeUnit.SECONDS.sleep(3);
		lock.clientServerAcquiredLock();
		TimeUnit.SECONDS.sleep(4);
		lock.clientServerAcquiredLock();
		latch.await();
	}

}

