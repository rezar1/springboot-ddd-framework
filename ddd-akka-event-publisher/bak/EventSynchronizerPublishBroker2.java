//package com.zero.ddd.akka.event.publisher2.actor.broker;
//
//import java.util.HashMap;
//import java.util.List;
//import java.util.Map;
//import java.util.Set;
//import java.util.stream.Collectors;
//
//import com.zero.ddd.akka.cluster.core.initializer.serializer.SelfProtoBufObject;
//import com.zero.ddd.akka.event.publisher2.actor.ServiceKeyHolder;
//import com.zero.ddd.akka.event.publisher2.actor.consumer.EventSynchConsuemr.EventSynchConsuemrEvent;
//import com.zero.ddd.akka.event.publisher2.domain.synchronizerState.PartitionAssignState;
//import com.zero.ddd.akka.event.publisher2.domain.synchronizerState.SynchronizerState;
//import com.zero.ddd.akka.event.publisher2.event.EventSynchronizer;
//import com.zero.ddd.akka.event.publisher2.publisher.PartitionEventPublisher;
//
//import akka.actor.typed.ActorRef;
//import akka.actor.typed.Behavior;
//import akka.actor.typed.javadsl.ActorContext;
//import akka.actor.typed.javadsl.Behaviors;
//import akka.actor.typed.receptionist.Receptionist;
//import akka.actor.typed.receptionist.Receptionist.Listing;
//import akka.actor.typed.receptionist.ServiceKey;
//import lombok.AllArgsConstructor;
//import lombok.Data;
//import lombok.NoArgsConstructor;
//import lombok.RequiredArgsConstructor;
//import lombok.ToString;
//import lombok.extern.slf4j.Slf4j;
//
///**
// * 
// * @say little Boy, don't be sad.
// * @name Rezar
// * @time 2023-06-12 03:58:36
// * @Desc 些年若许,不负芳华.
// *
// */
//@Slf4j
//public class EventSynchronizerPublishBroker2 {
//	
//	public static Behavior<EventSynchronizerBrokerEvent> create(
//			String eventSynchronizerId,
//			PartitionEventPublisher partitionEventPublisher) {
//		return Behaviors.setup(
//				context -> 
//					new EventSynchronizerPublishBroker2(
//							eventSynchronizerId,
//							partitionEventPublisher,
//							context)
//					.startEventPublish());
//	}
//	
//	private final String eventSynchronizerId;
//	private final PartitionEventPublisher partitionEventPublisher;
//	private final ActorContext<EventSynchronizerBrokerEvent> context;
//	private final ServiceKey<EventSynchConsuemrEvent> eventConsumerServiceKey;
//	private final Map<String, ActorRef<EventSynchConsuemrEvent>> onlineWorker = new HashMap<>();
//	
//	private SynchronizerState state;
//	
//	public EventSynchronizerPublishBroker2(
//			String eventSynchronizerId,
//			PartitionEventPublisher partitionEventPublisher,
//			ActorContext<EventSynchronizerBrokerEvent> context) {
//		this.context = context;
//		this.eventSynchronizerId = eventSynchronizerId;
//		this.partitionEventPublisher = partitionEventPublisher;
//		this.state = new SynchronizerState();
//		this.eventConsumerServiceKey = 
//				ServiceKeyHolder.eventConsumerServiceKey(
//						this.eventSynchronizerId);
//		this.registeAsSchedulerService();
//		this.subscribeEventConsumerService();
//		log.info(
//				"事件主题:[{}] 启动成功!!", 
//				this.eventSynchronizerId);
//	}
//	
//	private Behavior<EventSynchronizerBrokerEvent> startEventPublish() {
//		return Behaviors
//				.receive(EventSynchronizerBrokerEvent.class)
//				.onMessage(EventSynchronizerInfoReport.class, this::onEventSynchronizerInfoReport)
//				.onMessage(EventConsumerListingResponse.class, this::updateOnlineEventConsumerList)
//				.build();
//	}
//	
//	// 客户端上报当前配置的事件
//	private Behavior<EventSynchronizerBrokerEvent> onEventSynchronizerInfoReport(
//			EventSynchronizerInfoReport changed) {
//		log.info("EventSynchronizerInfoReport:{}", changed);
//		if (this.state.tryRefresh(
//				changed.getEventSynchronizer())) {
//			// 调整分片，关注事件类型等操作
//			this.partitionEventPublisher.refreshEventSynchronizerConfig(
//					this.state.getEventSynchronizer());
//		}
//		return Behaviors.same();
//	}
//	
//	private Behavior<EventSynchronizerBrokerEvent> updateOnlineEventConsumerList(
//			EventConsumerListingResponse changed) {
//		Set<ActorRef<EventSynchConsuemrEvent>> services = 
//				changed.getServices(eventConsumerServiceKey);
//		Map<String, ActorRef<EventSynchConsuemrEvent>> currentOnlieWorker = 
//				services.stream()
//				.collect(
//						Collectors.toMap(
//								ref -> ref.path().name(), 
//								ref -> ref));
//		log.info(
//				"事件主题:[{}] 刷新当前在线消费节点:{}", 
//				this.eventSynchronizerId, 
//				currentOnlieWorker.keySet());
//		Set<String> onlineEventConsumer = 
//				currentOnlieWorker.keySet();
//		this.onlineWorker.clear();
//		this.onlineWorker.putAll(currentOnlieWorker);
//		if (this.state.hasSyncConfig()) {
//			// 如果存在配置，看下如何分配任务
//			this.state.refreshOnlineEventConsumer(
//					onlineEventConsumer);
//			this.dispatchPartition();
//		}
//		return Behaviors.same();
//	}
//
//	/**
//	 * 
//	 * @param confirmTo
//	 */
//	private void dispatchPartition() {
//		List<PartitionAssignState> waitDispatchTask = 
//				this.state.waitDispatchPartition(
//						this.onlineWorker.keySet());
//		// 不需要记录分布式缓存，每次重新进行分配，客户端只响应事件分发
//		waitDispatchTask.stream()
//		.forEach(waitAssign -> {
//			this.partitionEventPublisher.partitionEventRouteTo(
//					waitAssign.getPartitionId(),
//					this.onlineWorker.get(
//							waitAssign.getAssignedTo()));
//		});
//	}
//	
//	private void registeAsSchedulerService() {
//		this.context
//		.getSystem()
//		.receptionist()
//		.tell(
//				Receptionist.register(
//						ServiceKeyHolder.eventPublisherServiceKey(
//								this.eventSynchronizerId),
//						this.context.getSelf()));
//	}
//
//	/**
//	 * 订阅当前job所有执行节点服务列表的变更
//	 */
//	private void subscribeEventConsumerService() {
//		ActorRef<Listing> listingResponseAdapter = 
//				this.context
//				.messageAdapter(
//						Receptionist.Listing.class, 
//						EventConsumerListingResponse::new);
//		this.context
//		.getSystem()
//		.receptionist()
//		.tell(
//				Receptionist.subscribe(
//						eventConsumerServiceKey, 
//						listingResponseAdapter));
//	}
//	
//	public static interface EventSynchronizerBrokerEvent extends SelfProtoBufObject {
//	}
//
//	@Data
//	@ToString
//	@NoArgsConstructor
//	@AllArgsConstructor
//	public static class EventSynchronizerInfoReport implements EventSynchronizerBrokerEvent {
//		private EventSynchronizer eventSynchronizer;
//	}
//	
//	@ToString
//	@RequiredArgsConstructor
//	public static class EventConsumerListingResponse implements EventSynchronizerBrokerEvent {
//		
//		final Receptionist.Listing listing;
//		
//		public Set<ActorRef<EventSynchConsuemrEvent>> getServices(
//				ServiceKey<EventSynchConsuemrEvent> serviceKey) {
//			return listing.getServiceInstances(serviceKey);
//		}
//		
//	}
//	
//}