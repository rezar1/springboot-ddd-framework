package com.zero.ddd.akka.event.publisher2.helper;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.commons.lang3.tuple.Pair;

import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import com.zero.helper.GU;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2023-06-14 02:22:56
 * @Desc 些年若许,不负芳华.
 *
 */
public class ConsistencyHashAlgorithm {
	
	private static final int VIRTUAL_NODE_NUM = 1000;
	private static final HashFunction murmur3_128 = Hashing.murmur3_128();
	
	private SortedMap<Long, String> virtualNodes = new TreeMap<>();
    private Map<Integer, String> assignResult = new HashMap<>();
	private int partitionCount;
    
    public ConsistencyHashAlgorithm() {
    }
    
    public void refreshPartitionCount(
    		int partitionCount) {
    	this.partitionCount = partitionCount;
    }
    
    public List<PartitionChanged<String>> workIdListChanged(
    		List<String> curWorkerIds) {
    	if (GU.isNullOrEmpty(curWorkerIds)) {
    		// 没有消费者了
    		return this.assignResult
    				.entrySet()
    				.stream()
    				.map(entry -> {
    					return new PartitionChanged<String>(
    							entry.getKey(), 
    		        			null,
    		        			null);
    				})
    				.collect(Collectors.toList());
    	}
    	Collections.sort(curWorkerIds);
    	this.refreshVirtualHashCircle(
    			curWorkerIds);
    	 // 分配已有分片
        Map<Integer, String> newAssignResult = 
        		IntStream
        		.range(0, this.partitionCount)
		        .mapToObj(partition -> {
		        	return Pair.of(
		        			partition, 
		        			this.getServer(
		        					"Partition-" + partition));
		        })
		        .collect(Collectors.toMap(Pair::getLeft, Pair::getRight));
        List<PartitionChanged<String>> influencedPartition = 
        		newAssignResult
		        .entrySet()
		        .stream()
		        .filter(cur -> {
		        	int partition = cur.getKey();
		        	// 如果之前没有分配，即初始化状态
		        	if (!this.assignResult.containsKey(partition)) {
		        		return true;
		        	}
		        	return !this.assignResult.get(partition).contentEquals(cur.getValue());
		        })
		        .map(changedAssign -> {
		        	int partition = changedAssign.getKey();
		        	return new PartitionChanged<String>(
		        			partition, 
		        			this.assignResult.get(partition),
		        			changedAssign.getValue());
		        })
		        .collect(Collectors.toList());
        this.assignResult.clear();
        this.assignResult.putAll(newAssignResult);
        return influencedPartition;
    }
    
    private void refreshVirtualHashCircle(
    		List<String> newConsumerList) {
        this.virtualNodes.clear();
        for (String consumer : newConsumerList) {
            for (int i = 0; i < VIRTUAL_NODE_NUM; i++) {
                String virtualNodeName = getVirtualNodeName(consumer, i);
                long hash = 
                		murmur3_128.hashBytes(
                				virtualNodeName.getBytes()).asLong();
                virtualNodes.put(hash, virtualNodeName);
            }
        }
    }
    
    private String getServer(String widgetKey) {
    	if (this.virtualNodes.isEmpty()) {
    		return null;
    	}
    	long hash = 
    			murmur3_128.hashBytes(
    					widgetKey.getBytes())
    			.asLong();
        SortedMap<Long, String> subMap = virtualNodes.tailMap(hash);
        String virtualNodeName;
        if (subMap.isEmpty()) {
            virtualNodeName = virtualNodes.get(virtualNodes.firstKey());
        } else {
            virtualNodeName = subMap.get(subMap.firstKey());
        }
        return getRealNodeName(virtualNodeName);
    }
    
    private String getVirtualNodeName(String realName, int num) {
        return realName + "&&VN" + num;
    }

    private String getRealNodeName(String virtualName) {
        return virtualName.split("&&")[0];
    }
    
    @Getter
    @ToString
    @AllArgsConstructor
    public static class PartitionChanged<T> {
    	private int partition;
    	private T oldAssignTo;
    	private T curAssignTo;
    	public boolean needStopPreConsumer() {
    		return this.oldAssignTo != null;
    	}
    	public boolean curAssignedConsumerNotExists() {
    		return this.curAssignTo == null;
    	}
    	
    	public boolean assignHashChanged() {
    		return this.curAssignTo != null
    				&& (this.oldAssignTo == null || !this.oldAssignTo.equals(curAssignTo));
    	}
    	
    }
    
}