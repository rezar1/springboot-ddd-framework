package com.zero.ddd.akka.event.publisher2.domain.partitionEvent;

import java.util.List;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2023-06-12 03:51:19
 * @Desc 些年若许,不负芳华.
 *
 */
public interface PartitionEventStore {
	
//	public void storePartitionEvent(PartitionEvent partitionEvent);
	
	public boolean storePartitionEvent(List<PartitionEvent> storedEventList);

}