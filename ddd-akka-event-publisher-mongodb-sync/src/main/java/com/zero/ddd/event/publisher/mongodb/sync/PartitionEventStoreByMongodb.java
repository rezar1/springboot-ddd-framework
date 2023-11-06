package com.zero.ddd.event.publisher.mongodb.sync;

import java.util.List;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;

import com.zero.ddd.akka.event.publisher2.domain.partitionEvent.PartitionEvent;
import com.zero.ddd.akka.event.publisher2.domain.partitionEvent.PartitionEventStore;
import com.zero.helper.JacksonUtil;

import lombok.extern.slf4j.Slf4j;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2023-06-13 05:57:46
 * @Desc 些年若许,不负芳华.
 *
 */
@Slf4j
public class PartitionEventStoreByMongodb implements PartitionEventStore {
	
	private static final String PARTITION_STORED_EVENT = "partition_event_sync";
	
	@Autowired
	private MongoTemplate mongoTemplate;
	
	@PostConstruct
	public void afterPost() {
		if (this.mongoTemplate.collectionExists(
				PARTITION_STORED_EVENT)) {
			return;
		}
		this.mongoTemplate.createCollection(
				PARTITION_STORED_EVENT);
		this.mongoTemplate.indexOps(
				PARTITION_STORED_EVENT)
		.ensureIndex(
				new Index()
				.on("synchronizerId", Sort.Direction.ASC)
				.on("eventId", Sort.Direction.ASC)
				.unique());
		this.mongoTemplate.indexOps(
				PARTITION_STORED_EVENT)
		.ensureIndex(
				new Index()
				.on("synchronizerId", Sort.Direction.ASC)
				.on("partition", Sort.Direction.ASC)
				.on("insertTime", Sort.Direction.ASC)
				.unique());
		this.mongoTemplate.indexOps(
				PARTITION_STORED_EVENT)
		.ensureIndex(
				new Index()
				.on("eventTime", Sort.Direction.ASC));
	}

	@Override
	public boolean storePartitionEvent(
			List<PartitionEvent> storedEventList) {
		storedEventList.forEach(event -> {
			try {
				this.mongoTemplate.insert(
						event, 
						PARTITION_STORED_EVENT);
			} catch (Exception e) {
				log.warn("storePartitionEvent:{} error", JacksonUtil.obj2Str(event));
				log.error("storePartitionEvent error:{}", e);
			}
		});
		return true;
	}

}

