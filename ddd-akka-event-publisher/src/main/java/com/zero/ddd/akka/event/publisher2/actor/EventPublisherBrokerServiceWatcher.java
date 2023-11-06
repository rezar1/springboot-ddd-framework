package com.zero.ddd.akka.event.publisher2.actor;

import java.time.Duration;
import java.util.Set;

import com.zero.ddd.akka.event.publisher2.actor.EventPublisherBrokerServiceWatcher.EventPublisherBrokerServiceWatcherCommnad;
import com.zero.ddd.akka.event.publisher2.actor.broker.EventSynchronizerPublishBroker.EventSynchronizerBrokerEvent;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import akka.actor.typed.receptionist.Receptionist;
import akka.actor.typed.receptionist.Receptionist.Listing;
import akka.actor.typed.receptionist.ServiceKey;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2023-04-25 12:52:07
 * @Desc 些年若许,不负芳华.
 *
 */
@Slf4j(topic = "event")
public class EventPublisherBrokerServiceWatcher extends AbstractBehavior<EventPublisherBrokerServiceWatcherCommnad> {
	
	public static Behavior<EventPublisherBrokerServiceWatcherCommnad> create(
			String eventSynchronizerId,
			ShardingPinger shardingPing) {
		return Behaviors.setup(context -> {
			return new EventPublisherBrokerServiceWatcher(
					eventSynchronizerId,
					shardingPing,
					context);
		});
	}
	
	private final String eventSynchronizerId;
	private final ShardingPinger shardingPing;
	private final ServiceKey<EventSynchronizerBrokerEvent> jobSchedulerServiceKey;
	
	public EventPublisherBrokerServiceWatcher(
			String eventSynchronizerId,
			ShardingPinger shardingPing,
			ActorContext<EventPublisherBrokerServiceWatcherCommnad> context) {
		super(context);
		this.shardingPing = shardingPing;
		this.eventSynchronizerId = eventSynchronizerId;
		this.jobSchedulerServiceKey = 
				ServiceKeyHolder.eventPublisherServiceKey(
						this.eventSynchronizerId);
		this.subscribeSchedulerService();
		doPing();
	}

	private void doPing() {
		if (!this.shardingPing.ping()) {
			super.getContext().scheduleOnce(
					Duration.ofSeconds(5), 
					super.getContext().getSelf(), 
					JustPing.INSTANCE);
		}
	}

	private void subscribeSchedulerService() {
		ActorRef<Listing> messageAdapter = 
				super.getContext()
				.messageAdapter(
						Receptionist.Listing.class, 
						listing -> {
							return new EventPublisherBrokerListingResponse(
									listing.getServiceInstances(
											this.jobSchedulerServiceKey));
						});
		super.getContext().getSystem()
		.receptionist()
		.tell(
				Receptionist.subscribe(
						this.jobSchedulerServiceKey, 
						messageAdapter));
	}

	@Override
	public Receive<EventPublisherBrokerServiceWatcherCommnad> createReceive() {
		return super.newReceiveBuilder()
				.onMessage(
						EventPublisherBrokerListingResponse.class, 
						services -> {
							if (services.isEmpty()) {
								log.info(
										"事件主题:[{}] 下线，发起ping请求完成Broker节点重新启动",
										this.eventSynchronizerId);
								this.doPing();
							}
							return this;
						})
				.onMessageEquals(JustPing.INSTANCE, () -> {
					this.doPing();
					return this;
				})
				.build();
	}
	
	@FunctionalInterface
	public static interface ShardingPinger {
		public boolean ping();
	}
	
	public static interface EventPublisherBrokerServiceWatcherCommnad {


	}
	
	@ToString
	@RequiredArgsConstructor
	public static class EventPublisherBrokerListingResponse implements EventPublisherBrokerServiceWatcherCommnad {
		
		final Set<ActorRef<EventSynchronizerBrokerEvent>> serviceInstances;
		boolean isEmpty() {
			return this.serviceInstances.isEmpty();
		}
		
	}
	
	public static enum JustPing implements EventPublisherBrokerServiceWatcherCommnad {
		INSTANCE
	}

}