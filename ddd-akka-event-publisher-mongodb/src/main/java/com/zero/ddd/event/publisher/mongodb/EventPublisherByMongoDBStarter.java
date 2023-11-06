package com.zero.ddd.event.publisher.mongodb;

import org.springframework.beans.factory.annotation.Configurable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.zero.ddd.akka.event.publisher2.domain.partitionEvent.PartitionEventStore;
import com.zero.ddd.akka.event.publisher2.event.IRecordLastOffsetId;
import com.zero.ddd.akka.event.publisher2.publisher.EventPublisherFactory;
import com.zero.ddd.core.event.store.EventStore;
import com.zero.ddd.event.publisher.mongodb.mongodb.EventPublisherFactoryByMongodb;
import com.zero.ddd.event.publisher.mongodb.mongodb.MongodbEventStore;
import com.zero.ddd.event.publisher.mongodb.mongodb.PartitionEventStoreByMongodb;
import com.zero.ddd.event.publisher.mongodb.mongodb.RecordLastOffsetIdByMongodb;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2023-07-03 11:26:58
 * @Desc 些年若许,不负芳华.
 *
 */
@Configurable
public class EventPublisherByMongoDBStarter {
	
	@Bean
	@ConditionalOnMissingBean(ReactiveMongoTemplate.class)
	public ReactiveMongoTemplate changeStreamReactiveMongoTemplate(
			@Value("${spring.data.mongodb.uri}") String url,
			@Value("${spring.data.mongodb.database}") String database) {
		MongoClientSettings settings = 
				MongoClientSettings.builder()
                .applyConnectionString(
                		new ConnectionString(url))
                .build();
		MongoClient client = MongoClients.create(settings);
		return new ReactiveMongoTemplate(client, database);
	}

	@Bean
	@ConditionalOnMissingBean(EventPublisherFactory.class)
	public EventPublisherFactory eventPublisherFactoryByMongodb(
			ReactiveMongoTemplate reactiveMongoTemplate) {
		return new EventPublisherFactoryByMongodb(
				reactiveMongoTemplate);
	}
	
	@Bean
	@ConditionalOnMissingBean(EventStore.class)
	public EventStore mongodbEventStore() {
		return new MongodbEventStore();
	}
	
	@Bean
	@ConditionalOnMissingBean(PartitionEventStore.class)
	public PartitionEventStore partitionEventStoreByMongodb() {
		return new PartitionEventStoreByMongodb();
	}
	
	@Bean
	@ConditionalOnMissingBean(IRecordLastOffsetId.class)
	public IRecordLastOffsetId IRecordLastOffsetId() {
		return new RecordLastOffsetIdByMongodb();
	}

}