package com.zero.ddd.akka.event.publisher2;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.google.common.collect.Sets;
import com.zero.ddd.akka.event.publisher2.domain.synchronizerState.PartitionAssignState;
import com.zero.ddd.akka.event.publisher2.domain.synchronizerState.SynchronizerState;
import com.zero.ddd.akka.event.publisher2.event.EventSynchronizer;

import lombok.extern.slf4j.Slf4j;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2023-06-14 04:16:57
 * @Desc 些年若许,不负芳华.
 *
 */
@Slf4j
public class SynchronizerStateTest {
	
	@Test
	public void test() {
		SynchronizerState state = new SynchronizerState();
		state.tryRefresh(
				new EventSynchronizer(
						"appName",
						1,
						"Rezar",
						Sets.newHashSet("type1")));
		List<PartitionAssignState> waitDispatchTask = 
				state.waitDispatchPartition(
						Sets.newHashSet("W-0"));
		log.info("waitDispatchTask:{}", waitDispatchTask);
		state.tryRefresh(
				new EventSynchronizer(
						"appName",
						5,
						"Rezar",
						Sets.newHashSet("type1")));
		waitDispatchTask = 
				state.waitDispatchPartition(
						Sets.newHashSet("W-0"));
		log.info("waitDispatchTask:{}", waitDispatchTask);
		waitDispatchTask = 
				state.waitDispatchPartition(
						Sets.newHashSet("W-0", "W-1", "W-3", "W-4"));
		log.info("waitDispatchTask:{}", waitDispatchTask);
	}

}