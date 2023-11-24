package com.zero.ddd.event.publisher.relational_database;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import com.zero.ddd.akka.event.publisher2.domain.partitionEvent.PartitionEventStore;
import com.zero.ddd.akka.event.publisher2.event.IRecordLastOffsetId;
import com.zero.ddd.akka.event.publisher2.publisher.EventPublisherFactory;
import com.zero.ddd.core.event.store.EventStore;
import com.zero.ddd.event.publisher.relational_database.define.EventPublisherFactoryByDatabase;
import com.zero.ddd.event.publisher.relational_database.define.EventStoreByDatabase;
import com.zero.ddd.event.publisher.relational_database.define.PartitionEventStoreByDatabase;
import com.zero.ddd.event.publisher.relational_database.define.RecordLastOffsetIdByDatabase;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2023-07-04 07:01:01
 * @Desc 些年若许,不负芳华.
 *
 */
@Configuration
public class RelationalDatabasePublisherStarter {
	
	@Bean
	@ConditionalOnMissingBean(name = "monitorStoredEventLoadOffsetScheduledExecutor")
	public ScheduledExecutorService monitorStoredEventLoadOffsetScheduledExecutor() {
		return 
				Executors.newSingleThreadScheduledExecutor(r -> {
					return 
							new Thread(r, "DDD_STORED_EVENT加载表记录卡顿检查线程");
				});
	}
	
	@Bean
	@ConditionalOnMissingBean(EventPublisherFactory.class)
	public EventPublisherFactory eventPublisherFactoryByMysql(
			NamedParameterJdbcTemplate jdbcTemplate,
			ScheduledExecutorService scheduledExecutorService,
			@Value("${akka.event.storedEventTable:ddd_stored_event}") String storedEventTableName,
			@Value("${akka.event.partitionEventTable:ddd_partition_event}") String partitionEventTableName,
			@Value("${akka.event.storedEventPollMill:678}") long storedEventPollMill,
			@Value("${akka.event.partitionEventPollMill:789}") long partitionEventPollMill) {
		return new EventPublisherFactoryByDatabase(
				jdbcTemplate,
				scheduledExecutorService,
				storedEventTableName,
				partitionEventTableName,
				Duration.ofMillis(storedEventPollMill),
				Duration.ofMillis(partitionEventPollMill));
	}
	
	@Bean
	@ConditionalOnMissingBean(EventStore.class)
	public EventStore mongodbEventStore(
			JdbcTemplate jdbcTemplate,
			@Value("${akka.event.storedEventTable:ddd_stored_event}") String storedEventTableName) {
		return new EventStoreByDatabase(
				jdbcTemplate,
				storedEventTableName);
	}
	
	@Bean
	@ConditionalOnMissingBean(PartitionEventStore.class)
	public PartitionEventStore partitionEventStoreByMongodb(
			JdbcTemplate jdbcTemplate,
			@Value("${akka.event.partitionEventTable:ddd_partition_event}") String partitionEventTableName) {
		return new PartitionEventStoreByDatabase(
				jdbcTemplate,
				partitionEventTableName);
	}
	
	@Bean
	@ConditionalOnMissingBean(IRecordLastOffsetId.class)
	public IRecordLastOffsetId IRecordLastOffsetId(
			JdbcTemplate jdbcTemplate,
			@Value("${akka.event.consumeOffsetRecordTable:ddd_event_last_consume_offset}") String offsetTable) {
		return new RecordLastOffsetIdByDatabase(
				jdbcTemplate, 
				offsetTable);
	}
	
}