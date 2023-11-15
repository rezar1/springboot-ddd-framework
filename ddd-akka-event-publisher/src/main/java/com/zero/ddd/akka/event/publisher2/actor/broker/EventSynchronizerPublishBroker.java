package com.zero.ddd.akka.event.publisher2.actor.broker;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.reactivestreams.Publisher;

import com.google.common.cache.LoadingCache;
import com.zero.ddd.akka.cluster.core.initializer.config.BlockingIODispatcherSelector;
import com.zero.ddd.akka.cluster.core.initializer.serializer.SelfProtoBufObject;
import com.zero.ddd.akka.event.publisher2.actor.ServiceKeyHolder;
import com.zero.ddd.akka.event.publisher2.actor.consumer.EventSynchConsuemr.EventSynchConsuemrEvent;
import com.zero.ddd.akka.event.publisher2.actor.consumer.EventSynchConsuemr.PartitionEventCommand;
import com.zero.ddd.akka.event.publisher2.domain.partitionEvent.PartitionEvent;
import com.zero.ddd.akka.event.publisher2.domain.partitionEvent.PartitionEventStore;
import com.zero.ddd.akka.event.publisher2.domain.synchronizerState.PartitionAssignState;
import com.zero.ddd.akka.event.publisher2.domain.synchronizerState.SynchronizerState;
import com.zero.ddd.akka.event.publisher2.event.EventSynchronizer;
import com.zero.ddd.akka.event.publisher2.event.IRecordLastOffsetId;
import com.zero.ddd.akka.event.publisher2.publisher.EventPublisherFactory;
import com.zero.ddd.akka.event.publisher2.publisher.EventPublisherFactory.PartitionStoredEventWrapper;
import com.zero.ddd.akka.event.publisher2.publisher.EventPublisherFactory.StoredEventWrapper;
import com.zero.ddd.core.event.store.StoredEvent;
import com.zero.helper.GU;
import com.zero.helper.SimpleCacheBuilder;

import akka.NotUsed;
import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.PostStop;
import akka.actor.typed.PreRestart;
import akka.actor.typed.SupervisorStrategy;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.receptionist.Receptionist;
import akka.actor.typed.receptionist.Receptionist.Listing;
import akka.actor.typed.receptionist.ServiceKey;
import akka.stream.KillSwitches;
import akka.stream.Materializer;
import akka.stream.UniqueKillSwitch;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.Keep;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import akka.stream.typed.javadsl.ActorSink;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import lombok.var;
import lombok.extern.slf4j.Slf4j;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2023-06-12 03:58:36
 * @Desc 些年若许,不负芳华.
 *
 */
@Slf4j(topic = "event")
public class EventSynchronizerPublishBroker {
	
	public static Behavior<EventSynchronizerBrokerEvent> create(
			String eventSynchronizerId,
			Materializer materizizer,
			IRecordLastOffsetId iRecordLastOffsetId,
			PartitionEventStore partitionEventStore,
			EventPublisherFactory eventPublisherFactory) {
		return Behaviors.setup(
				context -> 
				new EventSynchronizerPublishBroker(
						eventSynchronizerId,
						materizizer,
						iRecordLastOffsetId,
						partitionEventStore,
						eventPublisherFactory,
						context)
				.startEventPublish());
	}
	
	private final String eventSynchronizerId;
	private final PartitionEventPublisher partitionEventPublisher;
	private final ActorContext<EventSynchronizerBrokerEvent> context;
	private final ServiceKey<EventSynchConsuemrEvent> eventConsumerServiceKey;
	private final Map<String, ActorRef<EventSynchConsuemrEvent>> onlineWorker = new HashMap<>();
	
	private SynchronizerState state;
	private LoadingCache<Integer, ActorRef<EventSynchronizerBrokerEvent>> sinkActorRouteActorCache;
	
	public EventSynchronizerPublishBroker(
			String eventSynchronizerId,
			Materializer materizizer,
			IRecordLastOffsetId iRecordLastOffsetId,
			PartitionEventStore partitionEventStore,
			EventPublisherFactory eventPublisherFactory,
			ActorContext<EventSynchronizerBrokerEvent> context) {
		this.context = context;
		this.eventSynchronizerId = eventSynchronizerId;
		this.partitionEventPublisher = 
				new PartitionEventPublisher(
						materizizer, 
						iRecordLastOffsetId, 
						partitionEventStore, 
						eventPublisherFactory);
		this.state = new SynchronizerState();
		this.eventConsumerServiceKey = 
				ServiceKeyHolder.eventConsumerServiceKey(
						this.eventSynchronizerId);
		this.initPartitionSinkActor();
		this.registeAsSchedulerService();
		this.subscribeEventConsumerService();
		log.info(
				"事件主题:[{}] 启动成功!!", 
				this.eventSynchronizerId);
	}
	
	private Behavior<EventSynchronizerBrokerEvent> startEventPublish() {
		return Behaviors
				.receive(EventSynchronizerBrokerEvent.class)
				.onMessage(EventSynchronizerInfoReport.class, this::onEventSynchronizerInfoReport)
				.onMessage(EventConsumerListingResponse.class, this::updateOnlineEventConsumerList)
				.onMessage(Passivate.class, this::onPassivate)
				.build();
	}
	
	private void initPartitionSinkActor() {
		this.sinkActorRouteActorCache = 
				SimpleCacheBuilder.instance(
						partitionId -> {
							return 
									context.spawn(
											Behaviors.supervise(
													Behaviors.receive(
															EventSynchronizerBrokerEvent.class)
													.onMessage(BrokerRoutePartitionEvent.class, this::onBrokerRoutePartitionEvent)
													.onMessage(BrokerRouteConsumerAckEvent.class, this::onBrokerRouteConsumerAckEvent)
													.onMessage(BrokerRouteConsumerNeedStartPartitionEventSync.class, this::onBrokerRouteConsumerNeedStartPartitionEventSync)
													.onMessage(BrokerRouteConsumerNeedCompletePartitionSync.class, this::onBrokerRouteConsumerNeedCompletePartitionSync)
													.onMessage(BrokerRoutePartitionSinkFail.class, this::onBrokerRoutePartitionSinkFail)
													.onSignal(
										                      PreRestart.class,
										                      signal -> {
										                    	  log.info(
																			"事件主题:[" + this.eventSynchronizerId + "] 分片:[" + partitionId + "] SinkRouter启动!!!");
										                        return Behaviors.same();
										                      })
													.onSignal(PostStop.class, signal -> {
														log.warn(
																"事件主题:[" + this.eventSynchronizerId + "] 分片:[" + partitionId + "] SinkRouter停止!!!");
									                    return Behaviors.same();
									                })
													.build())
											.onFailure(Exception.class, SupervisorStrategy.restart()), 
											eventSynchronizerId + "-SinkActor-" + partitionId,
											BlockingIODispatcherSelector.defaultDispatcher());
						});		
	}
	
	private ActorRef<EventSynchronizerBrokerEvent> partitionSinkActor(
			int partition) {
		return this.sinkActorRouteActorCache.getUnchecked(partition);
	}
	
	private Behavior<EventSynchronizerBrokerEvent> onPassivate(
			Passivate passivate) {
		this.partitionEventPublisher.shutdown();
		return Behaviors.stopped();
	}

	private Behavior<EventSynchronizerBrokerEvent> onBrokerRouteConsumerNeedStartPartitionEventSync(
			BrokerRouteConsumerNeedStartPartitionEventSync event) {
		event.replyOk();
		return Behaviors.same();
	}
	
	private Behavior<EventSynchronizerBrokerEvent> onBrokerRouteConsumerAckEvent(
			BrokerRouteConsumerAckEvent event) {
		this.partitionEventPublisher.storePartitionEventConsumedOffset(
				event.getPartition(),
				event.getConsumedOffset());
		event.replyTo.tell(ACK.INSTANCE);
		return Behaviors.same();
	}
	
	private Behavior<EventSynchronizerBrokerEvent> onBrokerRoutePartitionSinkFail(
			BrokerRoutePartitionSinkFail event) {
		log.error(
				"事件主题:[" + this.eventSynchronizerId + "] 分片:[" + event.getPartition() + "] 读取异常!!", 
				event.getEx());
		return Behaviors.same();
	}
	
	private Behavior<EventSynchronizerBrokerEvent> onBrokerRouteConsumerNeedCompletePartitionSync(
			BrokerRouteConsumerNeedCompletePartitionSync event) {
		log.info(
				"事件主题:[{}] 需要关闭分片:[{}] 的读取!!", 
				this.eventSynchronizerId,
				event.getPartition());
		return Behaviors.same();
	}
	
	private Behavior<EventSynchronizerBrokerEvent> onBrokerRoutePartitionEvent(
			BrokerRoutePartitionEvent event) {
		this.onlineWorker.get(
				event.getRouteToConsumerId())
		.tell(
				event.partitionEventCommand);
		return Behaviors.same();
	}
	
	// 客户端上报当前配置的事件
	private Behavior<EventSynchronizerBrokerEvent> onEventSynchronizerInfoReport(
			EventSynchronizerInfoReport changed) {
		log.info("EventSynchronizerInfoReport:{}", changed);
		if (this.state.tryRefresh(
				changed.getEventSynchronizer())) {
			// 调整分片，关注事件类型等操作
			this.partitionEventPublisher.refreshEventSynchronizerConfig(
					this.state.getEventSynchronizer());
			this.dispatchPartition();
		}
		return Behaviors.same();
	}
	
	private Behavior<EventSynchronizerBrokerEvent> updateOnlineEventConsumerList(
			EventConsumerListingResponse changed) {
		Set<ActorRef<EventSynchConsuemrEvent>> services = 
				changed.getServices(eventConsumerServiceKey);
		Map<String, ActorRef<EventSynchConsuemrEvent>> currentOnlieWorker = 
				services.stream()
				.collect(
						Collectors.toMap(
								ref -> ref.path().name(), 
								ref -> ref));
		log.info(
				"事件主题:[{}] 刷新当前在线消费节点:{}", 
				this.eventSynchronizerId, 
				currentOnlieWorker.keySet());
		Set<String> onlineEventConsumer = 
				currentOnlieWorker.keySet();
		this.onlineWorker.clear();
		this.onlineWorker.putAll(currentOnlieWorker);
		if (this.state.hasSyncConfig()) {
			// 如果存在配置，看下如何分配任务
			this.state.refreshOnlineEventConsumer(
					onlineEventConsumer);
			this.dispatchPartition();
		}
		return Behaviors.same();
	}

	/**
	 * 
	 * @param confirmTo
	 */
	private void dispatchPartition() {
		List<PartitionAssignState> waitDispatchTask = 
				this.state.waitDispatchPartition(
						this.onlineWorker.keySet());
		// 不需要记录分布式缓存，每次重新进行分配，客户端只响应事件分发
		waitDispatchTask.stream()
		.forEach(waitAssign -> {
			this.partitionEventPublisher.partitionEventRouteTo(
					waitAssign.getPartitionId(),
					waitAssign.getAssignedTo());
		});
	}
	
	private void registeAsSchedulerService() {
		this.context
		.getSystem()
		.receptionist()
		.tell(
				Receptionist.register(
						ServiceKeyHolder.eventPublisherServiceKey(
								this.eventSynchronizerId),
						this.context.getSelf()));
	}

	/**
	 * 订阅当前job所有执行节点服务列表的变更
	 */
	private void subscribeEventConsumerService() {
		ActorRef<Listing> listingResponseAdapter = 
				this.context
				.messageAdapter(
						Receptionist.Listing.class, 
						EventConsumerListingResponse::new);
		this.context
		.getSystem()
		.receptionist()
		.tell(
				Receptionist.subscribe(
						eventConsumerServiceKey, 
						listingResponseAdapter));
	}
	
	private class PartitionEventPublisher {
		
		private boolean hasStart;
		private UniqueKillSwitch storedEventSyncKillSwitch;
		
		private final Materializer materializer;
		private final IRecordLastOffsetId iRecordLastOffsetId;
		private final PartitionEventStore partitionEventStore;
		private final EventPublisherFactory eventPublisherFactory;
		private final Map<Integer, UniqueKillSwitch> partitionKillSwitchMap = new HashMap<>();
		
		private PartitionEventPublisher(
				Materializer materializer, 
				IRecordLastOffsetId iRecordLastOffsetId,
				PartitionEventStore partitionEventStore,
				EventPublisherFactory eventPublisherFactory) {
			this.materializer = materializer;
			this.iRecordLastOffsetId = iRecordLastOffsetId;
			this.partitionEventStore = partitionEventStore;
			this.eventPublisherFactory = eventPublisherFactory;
		}

		void partitionEventRouteTo(
				int partition,
				String partitionEventRouteToConsumerId) {
			if (this.partitionKillSwitchMap.containsKey(partition)) {
				this.partitionKillSwitchMap.remove(partition).shutdown();
			}
			ActorRef<EventSynchronizerBrokerEvent> partitionSinkActor = partitionSinkActor(partition);
			final Sink<PartitionStoredEventWrapper, NotUsed> sink = 
					ActorSink.actorRefWithBackpressure(
							partitionSinkActor,
							(responseActorRef, element) -> {
								StoredEvent partitionStoredEvent = element.getStoredEvent();
								if (log.isDebugEnabled()) {
									log.debug(
											"事件主题:[{}] partition sink:{}-{}\toffset:{}", 
											eventSynchronizerId,
											partitionStoredEvent.getEventId(), 
											partitionStoredEvent.getEventBody(),
											element.getEventOffset());
								}
								return new BrokerRoutePartitionEvent(
										partitionEventRouteToConsumerId,
										new PartitionEventCommand(
												partitionStoredEvent.getEventId(),
												partitionStoredEvent.getTypeName(),
												partitionStoredEvent.getEventBody(),
												partitionStoredEvent.getEventTime(),
												new BrokerRouteConsumerAckEvent(
														partition,
														element.getEventOffset(),
														responseActorRef),
												partitionSinkActor));
							},
							(responseActorRef) -> {
								return new BrokerRouteConsumerNeedStartPartitionEventSync(
										partitionEventRouteToConsumerId, 
										partition, 
										responseActorRef);
							},
							ACK.INSTANCE, 
							new BrokerRouteConsumerNeedCompletePartitionSync(
									partition),
							(exception) -> {
								log.error(
										"事件主题:[" + eventSynchronizerId + "] partition:[" + partition + "] sink eror:{}", exception);
								return 
										new BrokerRoutePartitionSinkFail(
												partition, 
												exception);
							});
			UniqueKillSwitch killSwitch = 
					Source.fromPublisher(
							this.partitionEventPublisher(
									partition))
					.viaMat(
							Flow.of(
									PartitionStoredEventWrapper.class)
							.joinMat(
									KillSwitches.singleBidi(),
									Keep.right()),
							Keep.right())
					.toMat(sink, Keep.left())
					.run(this.materializer);
			this.partitionKillSwitchMap.put(
					partition, 
					killSwitch);
			log.info(
					"事件主题:[{}] 开启分区:[{}] 的读取, 事件消费者:[{}]", 
					eventSynchronizerId, 
					partition, 
					partitionEventRouteToConsumerId);
		}

		public void shutdown() {
			if (this.storedEventSyncKillSwitch != null) {
				this.storedEventSyncKillSwitch.shutdown();
			}
			this.partitionKillSwitchMap
			.entrySet()
			.stream()
			.forEach(entry -> {
				entry.getValue().shutdown();
			});
			log.info("事件主题:[{}] 退出完毕", eventSynchronizerId);
		}

		public void storePartitionEventConsumedOffset(
				int partition, 
				String consumedOffset) {
			try {
				this.iRecordLastOffsetId.saveLastOffset(
						this.partitionStoredEventOffsetKey(partition),
						consumedOffset);
			} catch (Exception e) {
				log.error("error storePartitionEventConsumedOffset:{}", e);
			}
		}

		private Publisher<PartitionStoredEventWrapper> partitionEventPublisher(
				int partition) {
			return 
					this.eventPublisherFactory.partitionEventPublisher(
							eventSynchronizerId, 
							partition,
							this.iRecordLastOffsetId.lastOffset(
									this.partitionStoredEventOffsetKey(partition)));
		}

		void refreshEventSynchronizerConfig(
				EventSynchronizer eventSynchronizer) {
			if (!hasStart) {
				this.startStoredEventPublisher();
			} else {
				// 判斷是不是partition發生變化
				// 判斷關注的事件類型是不是發生變化
				// 關閉之前的同步器，基於新的配置開啟事件同步器
				if (this.storedEventSyncKillSwitch != null) {
					this.storedEventSyncKillSwitch.shutdown();
				}
				this.startStoredEventPublisher();
			}
		}

		private void startStoredEventPublisher() {
			var shardingHashValGenerator = 
					state.shardingHashValGenerator();
			Flow<StoredEventWrapper, String, UniqueKillSwitch> syncToPartitionFlow = 
					Flow.of(StoredEventWrapper.class)
					.filter(event -> {
						return GU.notNullAndEmpty(
								event.getStoredEvent().getEventBody())
								&& !event.getStoredEvent().getEventBody().contentEquals("{}");
					})
					.groupedWithin(20, Duration.ofMillis(10))
					.map(storedEventList -> {
						this.partitionEventStore.storePartitionEvent(
								storedEventList.stream()
								.map(StoredEventWrapper::getStoredEvent)
								.map(storedEvent -> {
									long hashVal = 
											shardingHashValGenerator.hashVal(
													storedEvent.getTypeName(), 
													storedEvent.getEventBody());
									if (hashVal < 0) {
										return null;
									}
									return new PartitionEvent(
											eventSynchronizerId,
											(int)(Math.abs(hashVal) % this.partitionSize()),
											storedEvent.getEventId(),
											storedEvent.getTypeName(),
											storedEvent.getEventBody(),
											storedEvent.getEventTime());
								})
								.filter(Objects::nonNull)
								.collect(Collectors.toList()));
						return storedEventList.get(
								storedEventList.size() - 1)
								.getEventOffset();
					})
					.joinMat(KillSwitches.singleBidi(), Keep.right());
			this.storedEventSyncKillSwitch = 
					Source.fromPublisher(
							this.storedEventPublisher())
					.viaMat(syncToPartitionFlow, Keep.right())
					.groupedWithin(100, Duration.ofSeconds(5))
					.map(list -> list.get(list.size() - 1))
					.toMat(
							Sink.foreach(
									storedEventOffset -> {
										this.iRecordLastOffsetId.saveLastOffset(
												storedEventOffsetKey(),
												storedEventOffset);
										}), 
							Keep.left())
					.run(this.materializer);
			log.info("事件主题:[{}] 开启分片事件的读取", eventSynchronizerId);
		}

		private long partitionSize() {
			return state.partitionCount();
		}

		private Publisher<StoredEventWrapper> storedEventPublisher() {
			Optional<String> lastOffset = 
					this.iRecordLastOffsetId.lastOffset(
							storedEventOffsetKey());
			log.info(
					"事件主题:[{}] 开启分片事件的读取, 恢复点:{}", 
					eventSynchronizerId, 
					lastOffset.orElse(""));
			return 
					this.eventPublisherFactory.storedEventPublisher(
							lastOffset,
							state.awareTypes());
		}

		private String storedEventOffsetKey() {
			return "STORED_EVENT_OFFSET:" + eventSynchronizerId;
		}
		
		private String partitionStoredEventOffsetKey(int partition) {
			return "PARTITION_STORED_EVENT_OFFSET:" + eventSynchronizerId + ":" + partition;
		}
		
	}
	
	public static interface EventSynchronizerBrokerEvent extends SelfProtoBufObject {
	}
	
	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class BrokerRoutePartitionEvent implements EventSynchronizerBrokerEvent {
		private String routeToConsumerId;
		private PartitionEventCommand partitionEventCommand;
	}
	
	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class BrokerRouteConsumerNeedStartPartitionEventSync implements EventSynchronizerBrokerEvent {
		private String routeToConsumerId;
		private int partition;
		private ActorRef<ACK> ack;
		
		public void replyOk() {
			this.ack.tell(ACK.INSTANCE);
		}
	
	}
	
	@Data
	@ToString
	@NoArgsConstructor
	@AllArgsConstructor
	public static class BrokerRouteConsumerNeedCompletePartitionSync implements EventSynchronizerBrokerEvent {
		private int partition;
	}
	
	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class BrokerRoutePartitionSinkFail implements EventSynchronizerBrokerEvent {
		
		private int partition;
		private Throwable ex;
		
	}
	
	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class BrokerRouteConsumerAckEvent implements EventSynchronizerBrokerEvent {
		private int partition;
		private String consumedOffset;
		private ActorRef<ACK> replyTo;
	}
	
	public static enum ACK implements EventSynchronizerBrokerEvent {
		
		INSTANCE,
		
	}
	
	public enum Passivate implements EventSynchronizerBrokerEvent {
		INSTANCE
	}
	
	public enum TriggerTicketEvent implements EventSynchronizerBrokerEvent {
		INSTANCE
	}

	@Data
	@ToString
	@NoArgsConstructor
	@AllArgsConstructor
	public static class EventSynchronizerInfoReport implements EventSynchronizerBrokerEvent {
		private EventSynchronizer eventSynchronizer;
	}
	
	@ToString
	@RequiredArgsConstructor
	public static class EventConsumerListingResponse implements EventSynchronizerBrokerEvent {
		
		final Receptionist.Listing listing;
		
		public Set<ActorRef<EventSynchConsuemrEvent>> getServices(
				ServiceKey<EventSynchConsuemrEvent> serviceKey) {
			return listing.getServiceInstances(serviceKey);
		}
		
	}
	
}