package com.zero.ddd.akka.event.publisher.mysql;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.lang3.RandomUtils;
import org.junit.Test;

import akka.actor.typed.ActorSystem;
import akka.actor.typed.javadsl.Behaviors;
import akka.stream.KillSwitches;
import akka.stream.Materializer;
import akka.stream.UniqueKillSwitch;
import akka.stream.javadsl.Keep;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2023-12-18 03:54:20
 * @Desc 些年若许,不负芳华.
 *
 */
@Slf4j
public class StreamTerminateTest {
	
	Materializer materializer = 
			Materializer.createMaterializer(
					ActorSystem.create(
							Behaviors.empty(), 
							"test"));
	
	@Test
	public void test() throws InterruptedException {
		UniqueKillSwitch runkillSwitch = 
				Source.fromPublisher(
						this.storedEventPublisher())
				.viaMat(KillSwitches.single(), Keep.right())
				.groupedWithin(100, Duration.ofSeconds(5))
				.map(list -> list.get(list.size() - 1))
				.toMat(
						Sink.foreach(
								lastOffset -> {
									System.out.println(
											"LastOffset:" + lastOffset);
								}), 
						Keep.left())
				.run(this.materializer);
		TimeUnit.SECONDS.sleep(8);
		runkillSwitch.shutdown();
		log.info("shutdown-----");
		TimeUnit.SECONDS.sleep(8);
	}

	private Flux<Integer> storedEventPublisher() {
		AtomicInteger idx = new AtomicInteger(0);
		return 
				Flux.interval(
						Duration.ofMillis(
								RandomUtils.nextLong(
										1,
										180)))
				.onBackpressureDrop(ticket -> {
					log.info("onBackpressureDrop");
				})
				.map(ticket -> {
//					if (idx.get() == 50) {
//						throw new IllegalStateException();
//					}
					return 
							idx.incrementAndGet();
				})
				.doOnComplete(() -> {
					log.info("doOnComplete");
				})
				.doAfterTerminate(() -> {
					log.info("doAfterTerminate");
				})
				.doOnTerminate(() -> {
					log.info("Terminated");
				})
				.onErrorResume(error -> {
					log.error("error:{}", error);
					return null;
				})
				.filter(Objects::nonNull);
	}

}