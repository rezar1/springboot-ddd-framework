package com.zero.ddd.akka.cluster.job.model.vo;

import java.nio.charset.Charset;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

import org.apache.commons.collections4.SetUtils;

import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import com.zero.helper.GU;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2023-04-27 11:01:46
 * @Desc 些年若许,不负芳华.
 *
 */
public class JobTaskAssignorByConsistentHash<T> implements JobTaskAssignor {
	
	private static final int VIRTUAL_NODE_COUNT = 10;
	private static final HashFunction hashFunction = Hashing.murmur3_128();
	
	private final Map<String, T> onlineWorker;
	private final SortedMap<Integer, String> circle = new TreeMap<>();

	public JobTaskAssignorByConsistentHash(
			Map<String, T> onlineWorker) {
		this.onlineWorker = onlineWorker;
		for (String server : onlineWorker.keySet()) {
			for (int i = 0; i < VIRTUAL_NODE_COUNT; i++) {
	            circle.put(
	            		hashFunction.hashString(
	            				server + i,
	            				Charset.defaultCharset())
	            		.asInt(),
	            		server);
	        }
		}
	}
	
	// 获取对象映射的节点
	@Override
    public String getServerAssignTo(String taskId) {
        if (circle.isEmpty()) {
            return null;
        }
        int hash = 
        		hashFunction.hashString(
        				taskId.toString(), 
        				Charset.defaultCharset())
        		.asInt();
        if (!circle.containsKey(hash)) {
            SortedMap<Integer, String> tailMap = circle.tailMap(hash);
            hash = tailMap.isEmpty() ? circle.firstKey() : tailMap.firstKey();
        }
        return circle.get(hash);
    }
    
	public T serverRef(String server) {
    	return this.onlineWorker.get(server);
    }
    
    public JobTaskAssignorByConsistentHash<T> refreshServers(
    		Map<String, T> onlineWorker) {
    	if (SetUtils.isEqualSet(
    			this.onlineWorker.keySet(),
    			onlineWorker.keySet())) {
    		return this;
    	}
    	return new JobTaskAssignorByConsistentHash<T>(onlineWorker);
    }

	public static <T> JobTaskAssignorByConsistentHash<T> defaultAssignor() {
		return new JobTaskAssignorByConsistentHash<>(GU.emptyMap());
	}

	@Override
	public Set<String> onlineWorker() {
		return this.onlineWorker.keySet();
	}
    
}