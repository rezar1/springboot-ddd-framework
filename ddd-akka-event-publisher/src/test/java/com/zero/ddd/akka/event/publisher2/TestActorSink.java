package com.zero.ddd.akka.event.publisher2;

import java.util.List;

import org.junit.jupiter.api.Test;

import akka.NotUsed;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.javadsl.Behaviors;
import akka.stream.Materializer;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.Keep;
import akka.stream.javadsl.Source;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2023-06-14 11:35:27
 * @Desc 些年若许,不负芳华.
 *
 */
@Slf4j
public class TestActorSink {
	
	Materializer materializer = 
			Materializer.createMaterializer(
					ActorSystem.create(
							Behaviors.empty(), 
							"test"));
	
	@Test
	public void groupBy() {
		Source.range(1, 10)
	    .groupBy(2, i -> i % 2 == 0) 	// create two sub-streams with odd and even numbers
	    .reduce(Integer::sum) // for each sub-stream, sum its elements
	    .mergeSubstreams() // merge back into a stream
	    .runForeach(System.out::println, materializer);
	}
	
	@Test
	public void testGroupAndMerge() {
		log.info("start-----");
		Flow<Integer, GroupIntEvent, NotUsed> groupFlow = 
				Flow.of(Integer.class)
//				.groupBy(5, val -> {
//					log.info("groupBy:{}", val);
//					return val % 5;
//				})
				.grouped(20)
				.map(GroupIntEvent::new);
//				.mergeSubstreams();
		Source.range(0, 1000)
//		.map(val -> {
//			log.info("val:{}", val);
//			return val;
//		})
		.viaMat(groupFlow, Keep.none())
		.runForeach(
				val -> {
					log.info("res:{}", val);
				}, 
				materializer);
		log.info("end-----");
	}
	
	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	static class GroupIntEvent {
		List<Integer> grouped;
	}

}