package com.zero.ddd.akka.event.publisher2.beanProcessor;

import com.zero.ddd.akka.event.publisher2.actor.broker.EventSynchronizerPublishBroker.EventSynchronizerBrokerEvent;

import akka.cluster.sharding.typed.javadsl.EntityTypeKey;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2023-06-26 08:16:01
 * @Desc 些年若许,不负芳华.
 *
 */
public class PublisherEntityTypeKeyCreator {
	
	public static EntityTypeKey<EventSynchronizerBrokerEvent> entityTypeKey(
			String appName) {
		return EntityTypeKey.create(
				EventSynchronizerBrokerEvent.class, 
				"eventSynchronizerBroker-" + appName);
	}

}