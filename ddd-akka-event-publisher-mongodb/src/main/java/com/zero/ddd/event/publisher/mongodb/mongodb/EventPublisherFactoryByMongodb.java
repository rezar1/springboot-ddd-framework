package com.zero.ddd.event.publisher.mongodb.mongodb;

import java.time.ZoneId;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import org.bson.BsonDocument;
import org.bson.BsonString;
import org.bson.BsonValue;
import org.bson.Document;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscription;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.ChangeStreamEvent;
import org.springframework.data.mongodb.core.ChangeStreamOptions;
import org.springframework.data.mongodb.core.ChangeStreamOptions.ChangeStreamOptionsBuilder;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.Criteria;

import com.mongodb.MongoQueryException;
import com.mongodb.client.model.changestream.FullDocument;
import com.zero.ddd.akka.event.publisher2.publisher.EventPublisherFactory;
import com.zero.ddd.core.event.store.StoredEvent;

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
	private static final String PARTITION_STORED_EVENT = "partition_event";
	
	private ReactiveMongoTemplate reactiveMongoTemplate;
	
	private final ChangeStreamEvent<Document> TICKET_EVENT = 
			new ChangeStreamEvent<>(null, null, null);
	
	@Autowired
	public EventPublisherFactoryByMongodb(
			ReactiveMongoTemplate reactiveMongoTemplate) {
		this.reactiveMongoTemplate = reactiveMongoTemplate;
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
//										Duration.ofMinutes(10), 
//										Duration.ofMinutes(10))
//								.map(val -> {
//									return 
//											TICKET_EVENT;
//								}))
						.filter(document -> {
							if (document == TICKET_EVENT) {
								// 为避免远古建立的changeStream的resume token失效了，每隔10分钟从最后处理的resume token 重新连接
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
	
	@Override
	public Publisher<PartitionStoredEventWrapper> partitionEventPublisher(
			String eventSynchronizerId, 
			int partition,
			Optional<String> startAfterOffset) {
		AtomicReference<String> lastResumeToken = 
				new AtomicReference<>();
		AtomicReference<Subscription> subscription = new AtomicReference<>();
		Function<Optional<String>, Flux<PartitionStoredEventWrapper>> changeStreamFluxFactory = 
				offset -> {
					return 
							reactiveMongoTemplate.changeStream(
									PARTITION_STORED_EVENT, 
									this.partitionEventChangeStreamOptionsBuilder(
											offset,
											eventSynchronizerId, 
											partition), 
									Document.class)
							.doOnSubscribe(subscription::set)
//							.mergeWith(
//									Flux.interval(
//											Duration.ofMinutes(10), 
//											Duration.ofMinutes(10))
//									.map(val -> {
//										return 
//												TICKET_EVENT;
//									}))
							.filter(document -> {
								if (document == TICKET_EVENT) {
									// 为避免远古建立的changeStream的resume token失效了，每隔10分钟从最后处理的resume token 重新连接
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
								return new PartitionStoredEventWrapper(
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

	private ChangeStreamOptions partitionEventChangeStreamOptionsBuilder(
			Optional<String> startAfterOffset, 
			String eventSynchronizerId,
			int partition) {
		ChangeStreamOptionsBuilder builder = 
				ChangeStreamOptions.builder()
				.filter(
		        		Aggregation.newAggregation(
		        				Aggregation.match(
		        						Criteria.where(
		        								"operationType")
		        						.is("insert")
		        						.and("fullDocument.synchronizerId")
		        						.is(eventSynchronizerId)
		        						.and("fullDocument.partition")
		        						.is(partition))))
				.fullDocumentLookup(FullDocument.DEFAULT);
		startAfterOffset.ifPresent(
				fromOffset -> {
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