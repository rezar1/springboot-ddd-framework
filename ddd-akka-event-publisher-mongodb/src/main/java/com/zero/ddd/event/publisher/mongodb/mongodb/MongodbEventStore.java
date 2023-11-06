package com.zero.ddd.event.publisher.mongodb.mongodb;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.stereotype.Component;

import com.zero.ddd.core.event.store.EventStore;
import com.zero.ddd.core.event.store.StoredEvent;
import com.zero.ddd.core.model.DomainEvent;
import com.zero.helper.JacksonUtil;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2023-06-13 05:31:21
 * @Desc 些年若许,不负芳华.
 *
 */
@Component
public class MongodbEventStore implements EventStore {
	
	private static final String DDD_STORED_EVENT = "ddd_stored_event";
	
	@Autowired
	private MongoTemplate mongoTemplate;
	
	@PostConstruct
	public void initMongoCollection() {
		if (this.mongoTemplate.collectionExists(
				DDD_STORED_EVENT)) {
			return;
		}
		this.mongoTemplate.createCollection(
				DDD_STORED_EVENT);
		this.mongoTemplate.indexOps(
				DDD_STORED_EVENT)
		.ensureIndex(
				new Index()
				.on(
						"typeName",
						Sort.Direction.ASC));
	}
	
	@Override
	public List<StoredEvent> allStoredEventsBetween(
			long lowStoredEventId,
			long highStoredEventId) {
		throw new UnsupportedOperationException();
	}

	@Override
	public List<StoredEvent> allStoredEventsSince(
			long storedEventId) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void append(
			DomainEvent domainEvent) {
		StoredEvent storedEvent = 
				new StoredEvent(
						UUID.randomUUID().toString(), 
						domainEvent.getClass().getSimpleName(), 
						JacksonUtil.obj2Str(domainEvent), 
						LocalDateTime.now());
		this.mongoTemplate.save(
				storedEvent, 
				DDD_STORED_EVENT);
	}

	@Override
	public long countStoredEvents() {
		throw new UnsupportedOperationException();
	}

}

