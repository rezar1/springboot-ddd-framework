package com.zero.ddd.akka.event.publisher2.publisher;

import java.util.Optional;
import java.util.Set;

import org.reactivestreams.Publisher;

import com.zero.ddd.core.event.store.StoredEvent;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2023-06-12 03:56:07
 * @Desc 些年若许,不负芳华.
 *
 */
public interface EventPublisherFactory {
	
	public Publisher<StoredEventWrapper> storedEventPublisher(
			Optional<String> startAfterOffset, 
			Set<String> awareEventTypes);
	
	public Publisher<PartitionStoredEventWrapper> partitionEventPublisher(
			String eventSynchronizerId,
			int partition,
			Optional<String> startAfterOffset);
	
	@Data
	@AllArgsConstructor
	public static class StoredEventWrapper {
		private final String eventOffset;
		private final StoredEvent storedEvent;
	}
	
	@Data
	@AllArgsConstructor
	public static class PartitionStoredEventWrapper {
		private final String eventOffset;
		private final StoredEvent storedEvent;
	}
	
}