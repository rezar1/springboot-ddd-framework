package com.zero.ddd.core.event.store.listener;

import org.springframework.context.event.EventListener;

import com.zero.ddd.core.event.store.EventStore;
import com.zero.ddd.core.model.DomainEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2023-06-13 05:24:05
 * @Desc 些年若许,不负芳华.
 *
 */
@Slf4j
@RequiredArgsConstructor
public class DomainEventStoreListener {

    private final EventStore eventStore;
    
    @EventListener
    public void storeEvents(DomainEvent domainEvent) {
    	if (eventStore == null) {
    		log.warn("eventStore not config, store event:{} failure", domainEvent);
    		return;
    	}
        this.eventStore.append(domainEvent);
    }
}
