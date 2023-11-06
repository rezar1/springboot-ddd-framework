package com.zero.ddd.akka.cluster.core.helper;

import akka.actor.ActorRef;
import akka.cluster.sharding.ShardCoordinator.ShardAllocationStrategy;
import scala.collection.immutable.IndexedSeq;
import scala.collection.immutable.Map;
import scala.collection.immutable.Set;
import scala.concurrent.Future;

public class MyShardAllocationStrategy implements ShardAllocationStrategy {

	@Override
	public Future<ActorRef> allocateShard(
			ActorRef requester,
			String shardId,
			Map<ActorRef, IndexedSeq<String>> currentShardAllocations) {
		return null;
	}

	@Override
	public Future<Set<String>> rebalance(
			Map<ActorRef, IndexedSeq<String>> currentShardAllocations,
			Set<String> rebalanceInProgress) {
		return null;
	}
	
}
