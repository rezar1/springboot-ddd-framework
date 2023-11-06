package com.zero.ddd.akka.event.publisher2.domain.partitionEvent;

import java.time.LocalDateTime;

import com.zero.ddd.akka.event.publisher2.helper.MicrTimeFormat;
import com.zero.ddd.core.event.store.StoredEvent;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2023-06-12 03:49:02
 * @Desc 些年若许,不负芳华.
 *
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class PartitionEvent extends StoredEvent {

	private String insertTime;
	private String synchronizerId;
	private int partition;
	
	public PartitionEvent(
			String synchronizerId,
			int partition,
			String eventId,
			String typeName, 
			String eventBody, 
			LocalDateTime eventTime) {
		super(eventId, typeName, eventBody, eventTime);
		this.synchronizerId = synchronizerId;
		this.partition = partition;
		this.insertTime = 
				MicrTimeFormat.currentFormatTime();
	}

}