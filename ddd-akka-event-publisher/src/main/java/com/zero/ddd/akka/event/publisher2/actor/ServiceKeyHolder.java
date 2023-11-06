package com.zero.ddd.akka.event.publisher2.actor;

import com.zero.ddd.akka.event.publisher2.actor.broker.EventSynchronizerPublishBroker.EventSynchronizerBrokerEvent;
import com.zero.ddd.akka.event.publisher2.actor.consumer.EventSynchConsuemr.EventSynchConsuemrEvent;

import akka.actor.typed.receptionist.ServiceKey;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2023-06-12 04:27:16
 * @Desc 些年若许,不负芳华.
 *
 */
public class ServiceKeyHolder {

	public static ServiceKey<EventSynchConsuemrEvent> eventConsumerServiceKey(String appNameAndSynchronizerId) {
		return ServiceKey.create(EventSynchConsuemrEvent.class, "EventConsumer-" + appNameAndSynchronizerId);
	}

	public static ServiceKey<EventSynchronizerBrokerEvent> eventPublisherServiceKey(String appNameAndSynchronizerId) {
		return ServiceKey.create(EventSynchronizerBrokerEvent.class, "EventPublisher-" + appNameAndSynchronizerId);
	}

}

