package com.zero.ddd.event.publisher.mongodb.sync.domain;

import java.time.LocalDateTime;

import com.zero.ddd.akka.event.publisher2.helper.MicrTimeFormat;
import com.zero.ddd.core.event.store.StoredEvent;

import lombok.Getter;
import lombok.Setter;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2023-07-14 11:24:26
 * @Desc 些年若许,不负芳华.
 *
 */
public class StoredEventWithInsertTime extends StoredEvent {
	
	@Getter
	@Setter
	public String insertTime;

	public StoredEventWithInsertTime() {
		super();
	}

	public StoredEventWithInsertTime(
			String eventId, 
			String typeName,
			String eventBody,
			LocalDateTime eventTime) {
		super(eventId, typeName, eventBody, eventTime);
		this.insertTime = MicrTimeFormat.currentFormatTime();
	}

}