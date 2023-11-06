package com.zero.ddd.akka.cluster.core.helper;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.apache.commons.lang3.RandomUtils;

import akka.actor.ActorRef;
import akka.actor.Address;
import akka.actor.typed.ActorSystem;
import akka.cluster.sharding.ShardCoordinator;
import akka.cluster.typed.Cluster;
import lombok.extern.slf4j.Slf4j;
import scala.collection.immutable.IndexedSeq;
import scala.concurrent.Future;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2023-06-13 03:43:02
 * @Desc 些年若许,不负芳华.
 *
 */
@Slf4j
public class AppNameShardAllocationStrategy extends ShardCoordinator.AbstractShardAllocationStrategy {
	
	private final ActorSystem<Void> system;
	
	public AppNameShardAllocationStrategy(
			ActorSystem<Void> system) {
		this.system = system;
	}
	
	@Override
	public Future<ActorRef> allocateShard(
            ActorRef requester,
            String shardId,
            Map<ActorRef, IndexedSeq<String>> currentShardAllocations) {
		log.info(
				"requester:{} shardId:{} and currentShardAllocations:{}", 
				requester, 
				shardId,
				currentShardAllocations);
        String[] entityIdParts = shardId.split("-");
        if (entityIdParts.length < 2) {
            return Future.successful(requester);
        }
        String appName = entityIdParts[0];
        List<ActorRef> regionActors = 
        		currentShardAllocations.keySet()
        		.stream()
                .filter(actorRef -> {
                	Address address = actorRef.path().address();
                    return 
                    		StreamSupport.stream(
                        			Cluster.get(
                        					this.system)
                        			.state()
                        			.getMembers().spliterator(),
                        			false)
                        	.filter(m -> m.address().equals(address))
                            .anyMatch(m -> m.hasRole(appName));
                })
                .collect(Collectors.toList());
        // 如果当前没有Region，则返回null，表示无法分配Shard
        if (regionActors.isEmpty()) {
            return Future.successful(requester);
        }
        return Future.successful(
        		regionActors.get(
        				RandomUtils.nextInt(
        						0, 
        						regionActors.size())));
    }
	
	@Override
    public Future<Set<String>> rebalance(
            Map<ActorRef, IndexedSeq<String>> currentShardAllocations,
            Set<String> rebalanceInProgress) {
        return Future.successful(java.util.Collections.emptySet());
    }
	    
}