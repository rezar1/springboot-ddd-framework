package com.zero.ddd.akka.event.publisher2.domain.synchronizerState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.commons.lang3.tuple.Pair;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.collect.Sets;
import com.zero.ddd.akka.cluster.core.initializer.serializer.SelfProtoBufObject;
import com.zero.ddd.akka.event.publisher2.beanProcessor.EventSynchronizerBeanProcessor.EventBatchConfig;
import com.zero.ddd.akka.event.publisher2.event.EventSynchronizer;
import com.zero.ddd.akka.event.publisher2.event.TypeFilterAndShardingHashValGenerator;
import com.zero.ddd.akka.event.publisher2.helper.ConsistencyHashAlgorithm;
import com.zero.ddd.akka.event.publisher2.helper.ConsistencyHashAlgorithm.PartitionChanged;
import com.zero.helper.GU;

import io.protostuff.Exclude;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2023-06-12 04:02:30
 * @Desc 些年若许,不负芳华.
 *
 */
@Data
@Slf4j
@NoArgsConstructor
public class SynchronizerState implements SelfProtoBufObject, Cloneable {
	
	private EventSynchronizer eventSynchronizer;
	private Map<Integer, PartitionAssignState> partitionStateMap = new HashMap<>();
	
	@Exclude
	@JsonIgnore
	private transient Map<String, List<Integer>> consumerAssignedPartition = new HashMap<>();
	@Exclude
	@JsonIgnore
	private transient ConsistencyHashAlgorithm hashAlgorithm = new ConsistencyHashAlgorithm();
	
	public boolean hasSyncConfig() {
		return this.eventSynchronizer != null;
	}
	
	public int partitionCount() {
		return this.eventSynchronizer.getPartition();
	}
	
	public Set<String> awareTypes() {
		return this.eventSynchronizer.getAwareEventTypes();
	}

	public boolean tryRefresh(
			EventSynchronizer eventSynchronizer) {
		if (this.eventSynchronizer == null
				|| !this.eventSynchronizer.equals(eventSynchronizer)) {
			this.eventSynchronizer = eventSynchronizer;
			// 分区数不支持从多调整到少(同步逻辑太麻烦了), 目前没有逻辑保证，先自己用自己约定好
			if (this.eventSynchronizer.getPartition() 
					> this.partitionStateMap.size()) {
				this.hashAlgorithm.refreshPartitionCount(
						this.eventSynchronizer.getPartition());
				int appendPartitionCount = 
						this.eventSynchronizer.getPartition() - this.partitionStateMap.size();
				int rangeEnd = 
						this.partitionStateMap.size() + appendPartitionCount;
				IntStream.range(
						this.partitionStateMap.size(), 
						rangeEnd)
				.forEach(partitionIndex -> {
					this.partitionStateMap.put(
							partitionIndex,
							new PartitionAssignState(
									partitionIndex));
				});
			}
			return true;
		}
		return false;
	}
	
	public void refreshOnlineEventConsumer(
			Set<String> onlineEventConsumer) {
		// 获取下线节点上分配的任务
		Sets.newHashSet(
				this.consumerAssignedPartition.keySet())
		.stream()
		.filter(assignedTaskWorker -> {
			return !onlineEventConsumer.contains(assignedTaskWorker);
		})
		.forEach(offlineWorker -> {
			try {
				this.consumerAssignedPartition.remove(
						offlineWorker)
				.forEach(partitionId -> {
					this.partitionStateMap.get(partitionId)
					.resetToWaitAssign();
					log.info(
							"事件主题 [{}] 下线节点:[{}]的分片\t:[{}] 将重置为待分配状态", 
							this.eventSynchronizer.getSynchornizerId(), 
							offlineWorker, 
							partitionId);
				});
			} catch (Exception e) {
				log.error("error while refreshOnlineEventConsumer:{}", e);
			}
		});
	}
	
	public List<PartitionAssignState> waitDispatchPartition(
			Set<String> currentOnlineConsumer) {
		if (currentOnlineConsumer.isEmpty()) {
			return GU.emptyList();
		}
		return 
				this.hashAlgorithm.workIdListChanged(
						new ArrayList<>(
								currentOnlineConsumer))
				.stream()
				.filter(
						PartitionChanged::assignHashChanged)
				.map(partitionChanged -> {
					return 
							this.reAssignTo(
									partitionChanged.getPartition(),
									partitionChanged.getOldAssignTo(),
									partitionChanged.getCurAssignTo());
				})
				.collect(Collectors.toList());
	}

	private PartitionAssignState reAssignTo(
			int partition,
			String oldAssignTo, 
			String curAssignTo) {
		PartitionAssignState partitionAssignState = 
				this.partitionStateMap.get(partition);
		partitionAssignState.assignTo(curAssignTo);
		if (oldAssignTo != null
				&& this.consumerAssignedPartition.containsKey(oldAssignTo)) {
			this.consumerAssignedPartition.get(oldAssignTo)
			.remove(new Integer(partition));
		}
		GU.addListIfNotExists(
				this.consumerAssignedPartition, 
				curAssignTo, 
				partition);
		return partitionAssignState;
	}

	@Override
	public SynchronizerState clone() throws CloneNotSupportedException {
		SynchronizerState clone = (SynchronizerState) super.clone();
		clone.setPartitionStateMap(
				this.partitionStateMap
				.entrySet()
				.stream()
				.map(entry -> {
					return Pair.of(
							entry.getKey(), 
							entry.getValue());
				})
				.collect(
						Collectors.toMap(
								Pair::getLeft,
								Pair::getRight)));
		return clone;
	}

	public ShardingHashValGenerator shardingHashValGenerator() {
		Map<String, TypeFilterAndShardingHashValGenerator> typeShardingHashValExpression = 
				this.eventSynchronizer.typeShardingHashValGenerator();
		return (typeName, eventBody) -> {
			if (typeShardingHashValExpression.containsKey(typeName)) {
				return typeShardingHashValExpression.get(
						typeName)
						.parseHashVal(eventBody);
			}
			return 0l;
		};
	}
	
	@FunctionalInterface
	public static interface ShardingHashValGenerator {
		public long hashVal(String typeName, String eventBody);
	}

	public Optional<EventBatchConfig> batchEventConsumeConfig() {
		return 
				Optional.ofNullable(
						this.eventSynchronizer.getEventBatchConsumeConfig());
	}

}