package com.zero.ddd.event.publisher.mongodb.sync;

import java.time.Duration;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import org.apache.commons.lang3.RandomUtils;
import org.bson.BsonDocument;
import org.bson.BsonString;
import org.bson.BsonValue;
import org.bson.Document;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscription;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Order;
import org.springframework.data.mongodb.core.ChangeStreamEvent;
import org.springframework.data.mongodb.core.ChangeStreamOptions;
import org.springframework.data.mongodb.core.ChangeStreamOptions.ChangeStreamOptionsBuilder;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.mongodb.MongoQueryException;
import com.mongodb.client.model.changestream.FullDocument;
import com.zero.ddd.akka.event.publisher2.helper.MicrTimeFormat;
import com.zero.ddd.akka.event.publisher2.publisher.EventPublisherFactory;
import com.zero.ddd.core.event.store.StoredEvent;
import com.zero.ddd.event.publisher.mongodb.sync.domain.StoredEventWithInsertTime;

import lombok.RequiredArgsConstructor;
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
	
	private static final ZoneId zoneId = ZoneId.systemDefault();  
	private static final String DDD_STORED_EVENT = "ddd_stored_event";
	private static final String PARTITION_STORED_EVENT = "partition_event_sync";
	private static final Pageable pageable = PageRequest.of(0, 1000, Sort.by(Order.asc("insertTime")));
	
	private final MongoTemplate normalTemplate;
	private final Duration partitionEventPollDuration;
	private final ReactiveMongoTemplate reactiveMongoTemplate;
	
	private final ChangeStreamEvent<Document> TICKET_EVENT = 
			new ChangeStreamEvent<>(null, null, null);
	
	@Autowired
	public EventPublisherFactoryByMongodb(
			MongoTemplate normalTemplate,
			Duration partitionEventPollDuration,
			ReactiveMongoTemplate reactiveMongoTemplate) {
		this.normalTemplate = normalTemplate;
		this.reactiveMongoTemplate = reactiveMongoTemplate;
		this.partitionEventPollDuration = partitionEventPollDuration;
	}

	@Override
	public Publisher<StoredEventWrapper> storedEventPublisher(
			Optional<String> startAfterOffset,
			Set<String> awareEventTypes) {
		AtomicReference<String> lastResumeToken = 
				new AtomicReference<>();
		AtomicReference<Subscription> subscription = new AtomicReference<>();
		Function<Optional<String>, Flux<StoredEventWrapper>> changeStreamFluxFactory = 
			offset -> {
				return 
						reactiveMongoTemplate.changeStream(
								DDD_STORED_EVENT, 
								this.dddStoreEventChangeStreamOptionsBuilder(
										offset,
										awareEventTypes), 
								Document.class)
						.doOnSubscribe(subscription::set)
//						.mergeWith(
//								Flux.interval(
//										Duration.ofSeconds(5), 
//										Duration.ofSeconds(5))
//								.map(val -> {
//									return 
//											TICKET_EVENT;
//								}))
						.filter(document -> {
							if (document == TICKET_EVENT) {
								if (lastResumeToken.get() != null) {
									throw ChangeStreamIntervalConnectException.RETRY;
								}
								return false;
							}
							return true;
						})
						.map(document -> {
							BsonValue resumeToken = document.getResumeToken();
							Document body = document.getBody();
							String resumeTokenStr = 
									resumeToken.asDocument().getString("_data").getValue();
							lastResumeToken.set(resumeTokenStr);
							return new StoredEventWrapper(
									resumeTokenStr, 
									parseToStoredEvent(body));
						});
			};
		return 
				changeStreamFluxFactory.apply(
						startAfterOffset)
				.onErrorResume(
						new FluxErrorResumer<>(
								changeStreamFluxFactory, 
								lastResumeToken,
								subscription));
	}
	
	private List<StoredEventWithInsertTime> queryPartitionStoredEvent(
			String eventSynchronizerId, 
			int partition,
			String lastSyncTime) {
		return
				this.normalTemplate.find(
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
		Cache<String, Boolean> eventExistsCache = 
				CacheBuilder.newBuilder().maximumSize(1500).build();
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
//					log.info("filter typeName:{} event:{}", event.getTypeName(), event.getEventId());
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
	
	@RequiredArgsConstructor
	public static class FluxErrorResumer<T> implements Function<Throwable, Flux<T>> {
		private final Function<Optional<String>, Flux<T>> changeStreamFluxFactory;
		private final AtomicReference<String> lastResumeToken;
		private final AtomicReference<Subscription> subscription;
		@Override
		public Flux<T> apply(Throwable error) {
			if (subscription.get() != null) {
				subscription.get().cancel();
			}
			if (error instanceof MongoQueryException
					&& error.getMessage().contains("Resume of change stream was not possible, as the resume point may no longer be in the oplog")) {
				log.info("resumeToken无效，将重置为当前位置继续订阅");
				return 
						changeStreamFluxFactory.apply(
								Optional.empty())
						.onErrorResume(this);
			} else if (error == ChangeStreamIntervalConnectException.RETRY) {
				log.info("从最新消费的resumeToken:[{}] 继续订阅", lastResumeToken.get());
				return 
						changeStreamFluxFactory.apply(
								Optional.ofNullable(
										lastResumeToken.get()))
						.onErrorResume(this);
			}
			log.error("error while watch change stream:{}", error);
			throw new IllegalStateException(error);
		}
	}
	
	private StoredEvent parseToStoredEvent(
			Document body) {
		return new StoredEvent(
				body.getString("eventId"),
				body.getString("typeName"),
				body.getString("eventBody"),
				body.getDate("eventTime").toInstant().atZone(zoneId).toLocalDateTime());
	}
	
	private ChangeStreamOptions dddStoreEventChangeStreamOptionsBuilder(
			Optional<String> startAfterOffset, 
			Set<String> awareEventTypes) {
		ChangeStreamOptionsBuilder builder = 
				ChangeStreamOptions.builder()
				.filter(
		        		Aggregation.newAggregation(
		        				Aggregation.match(
		        						Criteria.where(
		        								"operationType")
		        						.is("insert")
		        						.and("fullDocument.typeName")
		        						.in(awareEventTypes))))
				.fullDocumentLookup(FullDocument.DEFAULT);
		startAfterOffset.ifPresent(fromOffset -> {
			builder.startAfter(
					new BsonDocument(
							"_data", 
							new BsonString(fromOffset)));
		});
		return builder.build();
	}

	private static class ChangeStreamIntervalConnectException extends RuntimeException {

		private static final long serialVersionUID = 1L;
		
		public static final ChangeStreamIntervalConnectException RETRY = new ChangeStreamIntervalConnectException();
	
		
	}

}