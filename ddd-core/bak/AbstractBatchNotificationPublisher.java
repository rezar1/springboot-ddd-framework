package com.zero.ddd.core.event.notfication.publisher;

import java.util.List;

import javax.validation.constraints.NotNull;

import com.zero.ddd.core.event.notfication.tracker.PublishedEventTrackerStore;
import com.zero.ddd.core.event.notfication.tracker.PublishedNotificationTracker;
import com.zero.ddd.core.event.store.EventStore;
import com.zero.ddd.core.event.store.StoredEvent;
import com.zero.helper.GU;

import lombok.extern.slf4j.Slf4j;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2022-06-30 03:27:56
 * @Desc 些年若许,不负芳华.
 *
 */
@Slf4j
public abstract class AbstractBatchNotificationPublisher implements NotificationPublisher {

	protected String serverName;
	protected EventStore eventStore;
	protected PublishedEventTrackerStore publishedEventTrackerStore;
	
	protected volatile long lastPublishEventId = -1;

	public AbstractBatchNotificationPublisher(
			@NotNull String serverName, 
			EventStore eventStore,
			PublishedEventTrackerStore publishedEventTrackerStore) {
		this.serverName = serverName;
		this.eventStore = eventStore;
		this.publishedEventTrackerStore = publishedEventTrackerStore;
	}

	/**
	 * 存在重复发布的情况，转发器需要去重
	 */
	@Override
	public synchronized void publishNotifications() {
		PublishedNotificationTracker publishedNotificationTracker = 
				this.publishedEventTrackerStore.publishedEventTracker(
						serverName);
		List<StoredEvent> needPublishEvents = 
				this.listUnpublishedNotifications(
						publishedNotificationTracker.mostRecentPublishedEventId());
		if (GU.isNullOrEmpty(needPublishEvents)) {
			return;
		}
		try {
			
			GU.splitList(needPublishEvents, this.batchSize())
			.forEach(batch -> {
				if (this.batchPublish(batch)) {
					this.publishedEventTrackerStore.trackMostRecentPublishedEvent(
							publishedNotificationTracker,
							batch.get(batch.size() - 1).eventId());
				}
			});
//			for (StoredEvent notification : needPublishEvents) {
//				if (!this.publish(notification)) {
//					if (log.isDebugEnabled()) {
//						log.debug(
//								"publish event:{} publish failure:{}", 
//								notification.eventId(), 
//								notification.eventBody());
//					}
//					break;
//				} else {
//					if (log.isDebugEnabled()) {
//						log.debug(
//								"publish event:{} publish success:{}", 
//								notification.eventId(), 
//								notification.eventBody());
//					}
//					this.lastPublishEventId = notification.eventId();
//				}
//			}
			this.publishedEventTrackerStore.trackMostRecentPublishedEvent(
					publishedNotificationTracker,
					this.lastPublishEventId);
		} catch (Exception e) {
			log.warn(
					"NotificationPublisher problem: {}", e);
		}
	}
	

	protected int batchSize() {
		return 50;
	}

	protected abstract boolean batchPublish(
			List<StoredEvent> batchNotification);

	protected List<StoredEvent> listUnpublishedNotifications(
			long mostRecentPublishedEventId){
		return this.eventStore.allStoredEventsSince(
				mostRecentPublishedEventId);
	}

}
