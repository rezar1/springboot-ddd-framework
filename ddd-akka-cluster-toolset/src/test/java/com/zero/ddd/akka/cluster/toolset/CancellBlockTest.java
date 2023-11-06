package com.zero.ddd.akka.cluster.toolset;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.junit.Test;

import akka.actor.Cancellable;
import akka.actor.typed.ActorRef;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.javadsl.Behaviors;
import lombok.extern.slf4j.Slf4j;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2023-06-16 02:24:35
 * @Desc 些年若许,不负芳华.
 *
 */
@Slf4j
public class CancellBlockTest {
	
	@Test
	public void test1() throws InterruptedException {
		ActorSystem.create(
				Behaviors.setup(context -> {
					ActorRef<String> spawn = context.spawn(
							Behaviors.<String>setup(
									context1 -> {
										return Behaviors.receive(String.class)
												.onMessageEquals("Exit", () -> {
													Cancellable scheduleOnce = 
															context1.scheduleOnce(
																	Duration.ofSeconds(1), 
																	context.getSelf(), 
																	"Xixi");
													scheduleOnce.cancel();
													scheduleOnce.cancel();
													log.info("Msg:----");
//													throw new IllegalStateException();
													return Behaviors.same();
												})
												.build();
									}), "Test");
					spawn.tell("GHag");
					spawn.tell("Exit");
			return Behaviors.same();
		}), "flatMapMerge-example");
		TimeUnit.SECONDS.sleep(1000);
	}
	
	@Test
	public void test() throws InterruptedException {
		Runnable longRunningTask = () -> {
			try {
				TimeUnit.SECONDS.sleep(1000);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		};
		ExecutorService newFixedThreadPool = 
				Executors.newFixedThreadPool(2);
		ScheduledExecutorService newScheduledThreadPool = Executors.newScheduledThreadPool(2);
		newScheduledThreadPool.schedule(() -> {
			try {
				TimeUnit.SECONDS.sleep(1000);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}, 60, TimeUnit.SECONDS);
		
		Thread t = new Thread(() -> {
			try {
				ScheduledFuture<?> schedule = newScheduledThreadPool.schedule(() -> {
							try {
								TimeUnit.SECONDS.sleep(150);
							} catch (InterruptedException e) {
								e.printStackTrace();
							}
						}, 60, TimeUnit.SECONDS);
				TimeUnit.SECONDS.sleep(1);
				newFixedThreadPool.execute(longRunningTask);
				newFixedThreadPool.execute(longRunningTask);
				log.info("==========");
				log.info("cancel:{}", schedule.cancel(false));
				log.info("cancel:{}", schedule.cancel(false));
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		});
		t.start();
		TimeUnit.SECONDS.sleep(1000);
	}

}

