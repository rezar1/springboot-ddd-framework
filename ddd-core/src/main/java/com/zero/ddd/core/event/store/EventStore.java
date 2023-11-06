package com.zero.ddd.core.event.store;


import java.util.List;

import com.zero.ddd.core.model.DomainEvent;


public interface EventStore {

    public List<StoredEvent> allStoredEventsBetween(long lowStoredEventId, long highStoredEventId);

    public List<StoredEvent> allStoredEventsSince(long storedEventId);

    public void append(DomainEvent domainEvent);

    public long countStoredEvents();
    
}
