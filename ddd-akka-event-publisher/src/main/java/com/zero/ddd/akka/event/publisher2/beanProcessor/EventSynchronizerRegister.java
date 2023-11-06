package com.zero.ddd.akka.event.publisher2.beanProcessor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import com.zero.ddd.akka.cluster.core.initializer.actor.IAsSpawnActor;
import com.zero.ddd.akka.cluster.core.initializer.config.BlockingIODispatcherSelector;
import com.zero.ddd.akka.event.publisher2.actor.EventPublisherBrokerServiceWatcher;
import com.zero.ddd.akka.event.publisher2.actor.EventPublisherBrokerServiceWatcher.ShardingPinger;
import com.zero.ddd.akka.event.publisher2.actor.broker.EventSynchronizerPublishBroker;
import com.zero.ddd.akka.event.publisher2.actor.broker.EventSynchronizerPublishBroker.EventSynchronizerBrokerEvent;
import com.zero.ddd.akka.event.publisher2.actor.broker.EventSynchronizerPublishBroker.EventSynchronizerInfoReport;
import com.zero.ddd.akka.event.publisher2.actor.broker.EventSynchronizerPublishBroker.Passivate;
import com.zero.ddd.akka.event.publisher2.actor.consumer.EventSynchConsuemr;
import com.zero.ddd.akka.event.publisher2.domain.partitionEvent.PartitionEventStore;
import com.zero.ddd.akka.event.publisher2.event.CustomizedEventSynchronizer;
import com.zero.ddd.akka.event.publisher2.event.EventSynchronizer;
import com.zero.ddd.akka.event.publisher2.event.IRecordEventConsumeResult;
import com.zero.ddd.akka.event.publisher2.event.IRecordLastOffsetId;
import com.zero.ddd.akka.event.publisher2.publisher.EventPublisherFactory;
import com.zero.helper.GU;
import com.zero.helper.JacksonUtil;

import akka.actor.Address;
import akka.actor.typed.ActorRef;
import akka.actor.typed.javadsl.ActorContext;
import akka.cluster.sharding.typed.ShardingEnvelope;
import akka.cluster.sharding.typed.javadsl.ClusterSharding;
import akka.cluster.sharding.typed.javadsl.Entity;
import akka.cluster.typed.Cluster;
import akka.stream.Materializer;
import lombok.extern.slf4j.Slf4j;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2023-06-13 02:33:44
 * @Desc 些年若许,不负芳华.
 *
 */
@Slf4j
public class EventSynchronizerRegister implements IAsSpawnActor {
	
	private static final IRecordEventConsumeResult DO_NOTHING_RECORD = 
			new IRecordEventConsumeResult() {
				@Override
				public void recordResult(String synchornizerId, String eventId, String resultMsg) {
				}
			};
	
	private final List<CustomizedEventSynchronizer> allEventSynchronizer = new ArrayList<>();
	private Materializer materializer;
	private ClusterSharding clusterSharding;
	
	private String appName;
	private IRecordLastOffsetId iRecordLastOffsetId;
	private PartitionEventStore partitionEventStore;
	private EventPublisherFactory eventPublisherFactory;
	private IRecordEventConsumeResult iRecordEventConsumeResult;
	
	public EventSynchronizerRegister(
			String appName,
			IRecordLastOffsetId iRecordLastOffsetId,
			PartitionEventStore partitionEventStore,
			EventPublisherFactory eventPublisherFactory,
			IRecordEventConsumeResult iRecordEventConsumeResult) {
		this.appName = appName;
		this.iRecordLastOffsetId = iRecordLastOffsetId;
		this.partitionEventStore = partitionEventStore;
		this.eventPublisherFactory = eventPublisherFactory;
		this.iRecordEventConsumeResult = 
				iRecordEventConsumeResult == null ? 
						DO_NOTHING_RECORD : iRecordEventConsumeResult;
	}
	
	@Override
	public void spawn(ActorContext<Void> context) {
		this.clusterSharding = 
				ClusterSharding
				.get(context.getSystem());
		this.startSharding(this.appName);
		if (GU.notNullAndEmpty(
				this.allEventSynchronizer)) {
			materializer = Materializer.createMaterializer(
					context.getSystem());
			this.allEventSynchronizer
			.stream()
			.forEach(sync -> {
				log.info("注册的监听:{}", JacksonUtil.obj2Str(sync));
				this.startEventSynchronizerConsumer(
						context,
						sync);
			});
		}
	}

	private void startEventSynchronizerConsumer(
			ActorContext<Void> context,
			CustomizedEventSynchronizer sync) {
		EventSynchronizer eventSynchronizer = 
				sync.getEventSynchronizer();
		Address selfAddress = 
				Cluster.get(
						context.getSystem()).selfMember().address();
		String onlineHostPort = 
				selfAddress.hostPort().replaceAll(".*?@", "");
		IntStream.range(
				0, 
				sync.getConcurrency())
		.forEach(index -> {
			String consumerId = 
					"consumerId-" + onlineHostPort + "-" + UUID.randomUUID().toString().replace("-", "");
			context.spawn(
					EventSynchConsuemr.create(sync, this.iRecordEventConsumeResult), 
					consumerId,
					BlockingIODispatcherSelector.defaultDispatcher());
		});
		ActorRef<ShardingEnvelope<EventSynchronizerBrokerEvent>> startSharding = 
				this.startSharding(eventSynchronizer.getAppName());
		ShardingPinger shardingPing = 
				() -> {
					try {
						startSharding.tell(
								new ShardingEnvelope<>(
								eventSynchronizer.uniqueKey(),
								new EventSynchronizerInfoReport(eventSynchronizer)));
						return true;
					} catch (Exception e) {
						log.warn("无法ping发布服务:" + eventSynchronizer.getAppName(), e.getMessage());
					}
					return false;
				};
		context.spawn(
				EventPublisherBrokerServiceWatcher.create(
						sync.getEventSynchronizer().uniqueKey(),
						shardingPing), 
				"watcher-" + onlineHostPort + "-" + UUID.randomUUID().toString().replace("-", ""),
				BlockingIODispatcherSelector.defaultDispatcher());
	}

	private ActorRef<ShardingEnvelope<EventSynchronizerBrokerEvent>> startSharding(
			String appName) {
		return 
				this.clusterSharding
				.init(
						Entity.of(
								PublisherEntityTypeKeyCreator.entityTypeKey(
										appName), 
								entityContext -> 
									EventSynchronizerPublishBroker.create(
											entityContext.getEntityId(), 
											this.materializer, 
											this.iRecordLastOffsetId, 
											this.partitionEventStore,
											this.eventPublisherFactory))
						.withRole(appName)
						.withStopMessage(Passivate.INSTANCE)
						.withEntityProps(
								BlockingIODispatcherSelector.defaultDispatcher()));
	}

	public void registe(
			CustomizedEventSynchronizer synchronizer) {
		if (this.existsSameJob(
				synchronizer.getEventSynchronizer().uniqueKey())) {
			throw new IllegalArgumentException(
					"重复的订阅客户端, appName:" + synchronizer.getEventSynchronizer().getAppName() + ", synchronizerId:" + synchronizer.getEventSynchronizer().getSynchornizerId());
		}
		this.allEventSynchronizer.add(synchronizer);
	}
	
	private boolean existsSameJob(String uniqueKey) {
		return this.allEventSynchronizer.stream()
				.filter(end -> end.getEventSynchronizer().uniqueKey().contentEquals(uniqueKey))
				.findAny()
				.isPresent();
	}

}