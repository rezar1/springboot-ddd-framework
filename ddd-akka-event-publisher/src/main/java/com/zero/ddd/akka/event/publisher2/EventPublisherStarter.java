package com.zero.ddd.akka.event.publisher2;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Configurable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

import com.zero.ddd.akka.event.publisher2.beanProcessor.EventSynchronizerBeanProcessor;
import com.zero.ddd.akka.event.publisher2.beanProcessor.EventSynchronizerRegister;
import com.zero.ddd.akka.event.publisher2.domain.partitionEvent.PartitionEventStore;
import com.zero.ddd.akka.event.publisher2.event.IRecordEventConsumeResult;
import com.zero.ddd.akka.event.publisher2.event.IRecordLastOffsetId;
import com.zero.ddd.akka.event.publisher2.publisher.EventPublisherFactory;
import com.zero.ddd.core.event.publish.DomainEventPublisher;
import com.zero.ddd.core.event.store.EventStore;
import com.zero.ddd.core.event.store.listener.DomainEventStoreListener;

import lombok.extern.slf4j.Slf4j;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2023-03-01 11:23:58
 * @Desc 些年若许,不负芳华.
 *
 */
@Slf4j
@Configurable
public class EventPublisherStarter {
	
	@Bean
	public DomainEventPublisher domainEventPublisher() {
		log.info("DomainEventPublisher 注册完成");
		return new DomainEventPublisher();
	}
	
	@Bean
	@ConditionalOnMissingBean(DomainEventStoreListener.class)
	public DomainEventStoreListener domainEventStoreListener(
			@Autowired(required = false)  EventStore eventStore) {
		log.info("DomainEventStoreListener 注册完成");
		return new DomainEventStoreListener(eventStore);
	}
	
	@Bean
	public EventSynchronizerRegister eventSynchronizerRegister(
			@Value("${spring.application.name}") String appName,
			@Autowired(required = false) IRecordLastOffsetId iRecordLastOffsetId,
			@Autowired(required = false) PartitionEventStore partitionEventStore,
			@Autowired(required = false) EventPublisherFactory eventPublisherFactory,
			@Autowired(required = false) IRecordEventConsumeResult iRecordEventConsumeResult) {
		log.info("EventSynchronizerRegister 注册完成");
		return new EventSynchronizerRegister(
				appName,
				iRecordLastOffsetId,
				partitionEventStore,
				eventPublisherFactory,
				iRecordEventConsumeResult);
	}
	
	@Bean
	@ConditionalOnBean({ EventSynchronizerRegister.class })
	public EventSynchronizerBeanProcessor eventSynchronizerBeanProcessor(
			EventSynchronizerRegister register) {
		log.info("EventSynchronizerBeanProcessor 注册完成");
		return new EventSynchronizerBeanProcessor(true, register);
	}
	
}