//package com.zero.ddd.akka.event.publisher2.publisher;
//
//import java.time.Duration;
//import java.util.HashMap;
//import java.util.Map;
//import java.util.stream.Collectors;
//
//import org.reactivestreams.Publisher;
//
//import com.zero.ddd.akka.event.publisher2.actor.consumer.EventSynchConsuemr.ACK;
//import com.zero.ddd.akka.event.publisher2.actor.consumer.EventSynchConsuemr.ConsumerNeedCompletePartitionSync;
//import com.zero.ddd.akka.event.publisher2.actor.consumer.EventSynchConsuemr.ConsumerNeedStartPartitionEventSync;
//import com.zero.ddd.akka.event.publisher2.actor.consumer.EventSynchConsuemr.EventSynchConsuemrEvent;
//import com.zero.ddd.akka.event.publisher2.actor.consumer.EventSynchConsuemr.PartitionEventCommand;
//import com.zero.ddd.akka.event.publisher2.actor.consumer.EventSynchConsuemr.PartitionSinkFail;
//import com.zero.ddd.akka.event.publisher2.domain.partitionEvent.PartitionEvent;
//import com.zero.ddd.akka.event.publisher2.domain.partitionEvent.PartitionEventStore;
//import com.zero.ddd.akka.event.publisher2.event.EventSynchronizer;
//import com.zero.ddd.akka.event.publisher2.event.IRecordLastOffsetId;
//import com.zero.ddd.akka.event.publisher2.publisher.EventPublisherFactory.PartitionStoredEventWrapper;
//import com.zero.ddd.akka.event.publisher2.publisher.EventPublisherFactory.StoredEventWrapper;
//import com.zero.ddd.core.event.store.StoredEvent;
//
//import akka.NotUsed;
//import akka.actor.typed.ActorRef;
//import akka.stream.KillSwitches;
//import akka.stream.Materializer;
//import akka.stream.UniqueKillSwitch;
//import akka.stream.javadsl.Flow;
//import akka.stream.javadsl.Keep;
//import akka.stream.javadsl.Sink;
//import akka.stream.javadsl.Source;
//import akka.stream.typed.javadsl.ActorSink;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//
///**
// * 
// * @say little Boy, don't be sad.
// * @name Rezar
// * @time 2023-06-12 04:12:03
// * @Desc 些年若许,不负芳华.
// *
// */
//@Slf4j
//@RequiredArgsConstructor
//public class PartitionEventPublisher {
//	
//	private EventSynchronizer eventSynchronizer;
//	private UniqueKillSwitch storedEventSyncKillSwitch;
//	
//	private final Materializer materializer;
//	private final IRecordLastOffsetId iRecordLastOffsetId;
//	private final PartitionEventStore partitionEventStore;
//	private final EventPublisherFactory eventPublisherFactory;
//	private final Map<Integer, UniqueKillSwitch> partitionKillSwitchMap = new HashMap<>();
//	
//	public void partitionEventRouteTo(
//			int partition,
//			ActorRef<EventSynchConsuemrEvent> sinkActorRef) {
//		if (this.partitionKillSwitchMap.containsKey(partition)) {
//			this.partitionKillSwitchMap.remove(partition).shutdown();
//		}
//		final Sink<PartitionStoredEventWrapper, NotUsed> sink = 
//				ActorSink.actorRefWithBackpressure(
//						sinkActorRef,
//						(responseActorRef, element) -> {
//							StoredEvent partitionStoredEvent = element.getStoredEvent();
//							return new PartitionEventCommand(
//									partitionStoredEvent.getEventId(),
//									partitionStoredEvent.getTypeName(),
//									partitionStoredEvent.getEventBody(),
//									partitionStoredEvent.getEventTime(),
//									responseActorRef);
//						},
//						(responseActorRef) -> new ConsumerNeedStartPartitionEventSync(partition, responseActorRef),
//						ACK.INSTANCE, 
//						new ConsumerNeedCompletePartitionSync(
//								partition),
//						(exception) -> new PartitionSinkFail(partition, exception));
//		UniqueKillSwitch killSwitch = 
//				Source.fromPublisher(
//						this.partitionEventPublisher(
//								partition))
//				.viaMat(
//						Flow.of(
//								PartitionStoredEventWrapper.class)
//						.joinMat(
//								KillSwitches.singleBidi(),
//								Keep.right()),
//						Keep.right())
//				.toMat(sink, Keep.left())
//				.run(this.materializer);
//		this.partitionKillSwitchMap.put(
//				partition, 
//				killSwitch);
//		log.info("开启分区:{}的读取", partition);
//	}
//
//	private Publisher<PartitionStoredEventWrapper> partitionEventPublisher(
//			int partition) {
//		return 
//				this.eventPublisherFactory.partitionEventPublisher(
//						this.eventSynchronizer.uniqueKey(), 
//						partition,
//						this.iRecordLastOffsetId.lastOffset(
//								this.partitionStoredEventOffsetKey(partition)));
//	}
//
//	public void refreshEventSynchronizerConfig(
//			EventSynchronizer eventSynchronizer) {
//		if (this.eventSynchronizer == null) {
//			this.eventSynchronizer = eventSynchronizer;
//			this.startStoredEventPublisher();
//		} else if (!this.eventSynchronizer.equals(eventSynchronizer)) {
//			// 判斷是不是partition發生變化
//			// 判斷關注的事件類型是不是發生變化
//			// 關閉之前的同步器，基於新的配置開啟事件同步器
//			if (this.storedEventSyncKillSwitch != null) {
//				this.storedEventSyncKillSwitch.shutdown();
//			}
//			this.startStoredEventPublisher();
//		}
//	}
//
//	private void startStoredEventPublisher() {
//		Flow<StoredEventWrapper, String, UniqueKillSwitch> syncToPartitionFlow = 
//				Flow.of(StoredEventWrapper.class)
//				.groupedWithin(20, Duration.ofMillis(10))
//				.map(storedEventList -> {
//					try {
//						this.partitionEventStore.storePartitionEvent(
//								storedEventList.stream()
//								.map(StoredEventWrapper::getStoredEvent)
//								.map(storedEvent -> {
//										return new PartitionEvent(
//												this.eventSynchronizer.uniqueKey(),
//												storedEvent.optionPartitionHash()
//												.map(hashVal -> Math.abs(hashVal) % this.partitionSize())
//												.map(Long::intValue)
//												.orElse(0),
//												storedEvent.getEventId(),
//												storedEvent.getTypeName(),
//												storedEvent.getEventBody(),
//												storedEvent.getEventTime());
//								})
//								.collect(Collectors.toList()));
//					} catch (Exception e) {
//						log.error("error while partition:{}", e);
//						throw new IllegalStateException(e);
//					}
//					return storedEventList.get(
//							storedEventList.size() - 1)
//							.getEventOffset();
//				})
//				.joinMat(KillSwitches.singleBidi(), Keep.right());
//		this.storedEventSyncKillSwitch = 
//				Source.fromPublisher(
//						this.storedEventPublisher())
//				.viaMat(syncToPartitionFlow, Keep.right())
//				.toMat(
//						Sink.foreach(
//								storedEventOffset -> {
//									this.iRecordLastOffsetId.saveLastOffset(
//											storedEventOffsetKey(),
//											storedEventOffset);
//									}), 
//						Keep.left())
//				.run(this.materializer);
//	}
//
//	private long partitionSize() {
//		return this.eventSynchronizer.getPartition();
//	}
//
//	private Publisher<StoredEventWrapper> storedEventPublisher() {
//		return 
//				this.eventPublisherFactory.storedEventPublisher(
//						this.iRecordLastOffsetId.lastOffset(
//								storedEventOffsetKey()),
//						this.eventSynchronizer.getAwareEventTypes());
//	}
//
//	private String storedEventOffsetKey() {
//		return "STORED_EVENT_OFFSET:" + this.eventSynchronizer.uniqueKey();
//	}
//	
//	private String partitionStoredEventOffsetKey(int partition) {
//		return "PARTITION_STORED_EVENT_OFFSET:" + this.eventSynchronizer.uniqueKey() + ":" + partition;
//	}
//	
//}