package com.zero.ddd.event.publisher.mongodb.sync;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.commons.lang3.RandomUtils;
import org.reactivestreams.Publisher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Order;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.zero.ddd.akka.event.publisher2.helper.MicrTimeFormat;
import com.zero.ddd.akka.event.publisher2.publisher.EventPublisherFactory;
import com.zero.ddd.core.event.store.StoredEvent;
import com.zero.ddd.event.publisher.mongodb.sync.domain.StoredEventWithInsertTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2023-06-13 06:04:05
 * @Desc 些年若许,不负芳华.
 *
 */
@Slf4j
public class EventPublisherFactoryByMongodb implements EventPublisherFactory {
	
	private static final Pageable pageable = PageRequest.of(0, 1000, Sort.by(Order.asc("insertTime")));
	
	private static final String DDD_STORED_EVENT = "ddd_stored_event_sync";
	private static final String PARTITION_STORED_EVENT = "partition_event_sync";
	
	private final MongoTemplate mongoTemplate;
	private final Duration storedEventPollDuration;
	private final Duration partitionEventPollDuration;
	
	@Autowired
	public EventPublisherFactoryByMongodb(
			MongoTemplate mongoTemplate,
			Duration storedEventPollDuration,
			Duration partitionEventPollDuration) {
		this.mongoTemplate = mongoTemplate;
		this.storedEventPollDuration = storedEventPollDuration;
		this.partitionEventPollDuration = partitionEventPollDuration;
		
	}
	
	private List<StoredEventWithInsertTime> queryStoredEvent(
			String lastSyncTime,
			Set<String> awareEventTypes) {
		return
				this.mongoTemplate.find(
						new Query().addCriteria(
								Criteria.where("typeName")
								.in(awareEventTypes)
								.and("insertTime")
								.gt(lastSyncTime))
						.with(pageable),
						StoredEventWithInsertTime.class, 
						DDD_STORED_EVENT);
	}

	@Override
	public Publisher<StoredEventWrapper> storedEventPublisher(
			Optional<String> startAfterOffset,
			Set<String> awareEventTypes) {
		Cache<String, Boolean> eventExistsCache = CacheBuilder.newBuilder().maximumSize(1500).build();
		AtomicReference<String> lastSyncTime = 
				new AtomicReference<>(
						startAfterOffset
						.orElseGet(
								() -> MicrTimeFormat.currentFormatTime()));
		return 
				Flux.interval(
						storedEventPollDuration.plus(
								Duration.ofMillis(
										RandomUtils.nextLong(
												1,
												180))))
				.onBackpressureDrop()
				.flatMap(notUsed -> {
					return Flux.fromIterable(
							this.queryStoredEvent(
									lastSyncTime.get(),
									awareEventTypes));
				})
				.filter(Objects::nonNull)
				.filter(event -> {
					String eventId = event.getEventId();
					Boolean ifPresent = 
							eventExistsCache.getIfPresent(eventId);
					if (ifPresent == null) {
						eventExistsCache.put(eventId, true);
						return true;
					}
					log.info("filter typeName:{} event:{}", event.getTypeName(), event.getEventId());
					return false;
				})
				.map(event -> {
					lastSyncTime.set(
							event.getInsertTime());
					return 
							new StoredEventWrapper(
									event.getInsertTime(),
									event);
				})
				.onErrorResume(error -> {
					log.error("error:{}", error);
					return null;
				});
	}
	
	private List<StoredEventWithInsertTime> queryPartitionStoredEvent(
			String eventSynchronizerId, 
			int partition,
			String lastSyncTime) {
		return
				this.mongoTemplate.find(
						new Query().addCriteria(
								Criteria.where("synchronizerId")
								.is(eventSynchronizerId)
								.and("partition")
								.is(partition)
								.and("insertTime")
								.gt(lastSyncTime))
						.with(pageable),
						StoredEventWithInsertTime.class, 
						PARTITION_STORED_EVENT);
	}
	
	@Override
	public Publisher<PartitionStoredEventWrapper> partitionEventPublisher(
			String eventSynchronizerId, 
			int partition,
			Optional<String> startAfterOffset) {
		Cache<String, Boolean> eventExistsCache = CacheBuilder.newBuilder().maximumSize(1500).build();
		AtomicReference<String> lastSyncTime = 
				new AtomicReference<>(
						startAfterOffset
						.orElseGet(() -> MicrTimeFormat.currentFormatTime()));
		return 
				Flux.interval(
						partitionEventPollDuration.plus(
								Duration.ofMillis(
										RandomUtils.nextLong(
												1,
												180))))
				.onBackpressureDrop(ticket -> {
					log.warn(
							"eventSynchronizerId:[{}] partition:[{}] 消费速率慢，丢弃当前轮次的调度，等待下一次调度",
							eventSynchronizerId, 
							partition);
				})
				.flatMap(notUsed -> {
					return Flux.fromIterable(
							this.queryPartitionStoredEvent(
									eventSynchronizerId,
									partition,
									lastSyncTime.get()));
				})
				.filter(Objects::nonNull)
				.filter(event -> {
					String eventId = event.getEventId();
					Boolean ifPresent = 
							eventExistsCache.getIfPresent(eventId);
					if (ifPresent == null) {
						eventExistsCache.put(eventId, true);
						return true;
					}
					log.info("filter typeName:{} event:{}", event.getTypeName(), event.getEventId());
					return false;
				})
				.map(event -> {
					lastSyncTime.set(
							event.getInsertTime());
					return 
							new PartitionStoredEventWrapper(
									event.getInsertTime(),
									event);
				})
				.onErrorResume(error -> {
					log.error("error:{}", error);
					return null;
				});
	}
	
	@Data
	@AllArgsConstructor
	public static class StoredEventTimeWrapper {
		private final LocalDateTime eventOffset;
		private final StoredEvent storedEvent;
	}
	

}