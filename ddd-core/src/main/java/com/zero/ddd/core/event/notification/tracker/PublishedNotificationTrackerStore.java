package com.zero.ddd.core.event.notification.tracker;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2023-06-05 11:12:36
 * @Desc 些年若许,不负芳华.
 *
 */
public interface PublishedNotificationTrackerStore {

    public PublishedNotificationTracker publishedNotificationTracker();

    public PublishedNotificationTracker publishedNotificationTracker(String aTypeName);

}
