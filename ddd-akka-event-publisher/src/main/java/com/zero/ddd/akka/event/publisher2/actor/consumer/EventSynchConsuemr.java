package com.zero.ddd.akka.event.publisher2.actor.consumer;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

import org.springframework.util.backoff.BackOffExecution;
import org.springframework.util.backoff.ExponentialBackOff;

import com.zero.ddd.akka.cluster.core.initializer.serializer.SelfProtoBufObject;
import com.zero.ddd.akka.event.publisher2.actor.ServiceKeyHolder;
import com.zero.ddd.akka.event.publisher2.actor.broker.EventSynchronizerPublishBroker.BrokerRouteConsumerAckEvent;
import com.zero.ddd.akka.event.publisher2.actor.broker.EventSynchronizerPublishBroker.EventSynchronizerBrokerEvent;
import com.zero.ddd.akka.event.publisher2.actor.consumer.EventSynchConsuemr.EventSynchConsuemrEvent;
import com.zero.ddd.akka.event.publisher2.event.CustomizedEventSynchronizer;
import com.zero.ddd.akka.event.publisher2.event.IRecordEventConsumeResult;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import akka.actor.typed.receptionist.Receptionist;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2023-06-12 04:25:03
 * @Desc 些年若许,不负芳华.
 *
 */
@Slf4j(topic = "event")
public class EventSynchConsuemr extends AbstractBehavior<EventSynchConsuemrEvent> {
	
	public static Behavior<EventSynchConsuemrEvent> create(
			CustomizedEventSynchronizer synchronizer,
			IRecordEventConsumeResult iRecordEventConsumeResult) {
		return Behaviors.setup(context -> {
			return new EventSynchConsuemr(synchronizer, iRecordEventConsumeResult, context);
		});
	}
	
	private final String consumerShowName;
	private final ExponentialBackOff backoff;
	private final CustomizedEventSynchronizer eventSynchronizer;
	private final IRecordEventConsumeResult iRecordEventConsumeResult;
	
	public EventSynchConsuemr(
			CustomizedEventSynchronizer eventSynchronizer,
			IRecordEventConsumeResult iRecordEventConsumeResult,
			ActorContext<EventSynchConsuemrEvent> context) {
		super(context);
		this.backoff = this.initBackoff();
		this.eventSynchronizer = eventSynchronizer;
		this.iRecordEventConsumeResult = iRecordEventConsumeResult;
		this.consumerShowName = context.getSelf().path().name();
		this.startServiceRegiste();
		log.info(
				"事件主题:[{}]\t消费节点:[{}]启动完成", 
				this.eventSynchronizer.getEventSynchronizer().uniqueKey(), 
				this.consumerShowName);
	}
	
	private void startServiceRegiste() {
		super.getContext()
		.getSystem()
		.receptionist()
		.tell(
				Receptionist.register(
						ServiceKeyHolder.eventConsumerServiceKey(
								this.eventSynchronizer.getEventSynchronizer().uniqueKey()),
						super.getContext().getSelf()));
	}

	@Override
	public Receive<EventSynchConsuemrEvent> createReceive() {
		return super.newReceiveBuilder()
				.onMessage(ConsumerNeedStartPartitionEventSync.class, this::onConsumerNeedStartPartitionEventSync)
				.onMessage(PartitionEventCommand.class, this::onPartitionEventCommand)
				.onMessage(ConsumerNeedCompletePartitionSync.class, this::onConsumerNeedCompletePartitionSync)
				.build();
	}
	
	private Behavior<EventSynchConsuemrEvent> onConsumerNeedCompletePartitionSync(
			ConsumerNeedCompletePartitionSync command){
		log.info("onConsumerNeedCompletePartitionSync:{}", command);
		return Behaviors.same();
	}
	
//			FixedBackOff backOff = new FixedBackOff(0, 0);
	private Behavior<EventSynchConsuemrEvent> onPartitionEventCommand(
			PartitionEventCommand command) {
		if (log.isInfoEnabled()) {
			log.info(
					"事件主题:[{}] 获取到消费事件:[eventId:{}, eventTime:{}, eventType:{}, eventBody:{}]",
					this.eventSynchronizer.getEventSynchronizer().uniqueKey(),
					command.getEventId(),
					command.getEventTime(),
					command.getEventType(),
					command.getEventBody());
		}
		// 加个异常重试
		String invokeMsg = null;
		BackOffExecution execution = null;
		// 基于规则看是继续还是停止
		do {
			try {
				this.eventSynchronizer.getEventFunctionInvoker()
				.execute(
						command.getEventType(), 
						command.getEventBody());
				break;
			} catch (Exception e) {
				if (execution == null) {
					execution = backoff.start();
				}
				long nextBackOff = execution.nextBackOff();
				log.warn(
						"事件主题:[{}] 消费事件失败:[eventId:{}, eventTime:{}, eventType:{}, eventBody:{}] 将回避:[{}] mills",
						this.eventSynchronizer.getEventSynchronizer().uniqueKey(),
						command.getEventId(),
						command.getEventTime(),
						command.getEventType(),
						command.getEventBody(),
						nextBackOff);
				log.error("error while consumer:{}", e);
				if (nextBackOff == BackOffExecution.STOP) {
					break;
				}
				String msg = e.getMessage();
				invokeMsg = 
						e.getMessage() == null ? 
								"未知" : msg.length() > 1000 ? 
											msg.substring(0, 1000) : msg.substring(0,  msg.length());
				try {
					TimeUnit.MILLISECONDS.sleep(nextBackOff);
				} catch (InterruptedException e1) {
					break;
				}
			}
		} while(true);
		this.iRecordEventConsumeResult.recordResult(
				this.eventSynchronizer.getEventSynchronizer().uniqueKey(), 
				command.getEventId(),
				invokeMsg);
		command.replyOk();
		return Behaviors.same();
	}
	
	private ExponentialBackOff initBackoff() {
		long initialInterval = 200;// 初始间隔
		long maxInterval = 10 * 1000L;// 最大间隔
		long maxElapsedTime = 50 * 1000L;// 最大时间间隔
		double multiplier = 1.5;// 递增倍数（即下次间隔是上次的多少倍）
		ExponentialBackOff backOff = new ExponentialBackOff(initialInterval, multiplier);
		backOff.setMaxInterval(maxInterval);
		// currentElapsedTime = interval1 + interval2 + ... + intervalN;
		backOff.setMaxElapsedTime(maxElapsedTime);
		return backOff;
	}

	private Behavior<EventSynchConsuemrEvent> onConsumerNeedStartPartitionEventSync(
			ConsumerNeedStartPartitionEventSync command){
		log.info("onConsumerNeedStartPartitionEventSync:{}", command);
		log.info(
				"事件主题:[{}] 分片:{} 开启事件消费",
				this.eventSynchronizer.getEventSynchronizer().uniqueKey(),
				command.getPartition());
		command.replyOk();
		return Behaviors.same();
	}
	
	public static interface EventSynchConsuemrEvent extends SelfProtoBufObject {}
	
	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class ConsumerNeedStartPartitionEventSync implements EventSynchConsuemrEvent {
		private int partition;
		private ActorRef<ACK> ack;
		
		public void replyOk() {
			this.ack.tell(ACK.INSTANCE);
		}
	}
	
	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class PartitionEventCommand implements EventSynchConsuemrEvent {
		
		private String eventId;
		private String eventType;
		private String eventBody;
		private LocalDateTime eventTime;
		private BrokerRouteConsumerAckEvent ackEvent;
		private ActorRef<EventSynchronizerBrokerEvent> replyTo;
		
		public void replyOk() {
			replyTo.tell(ackEvent);
		}

	}
	
	@Data
	@ToString
	@NoArgsConstructor
	@AllArgsConstructor
	public static class ConsumerNeedCompletePartitionSync implements EventSynchConsuemrEvent {
		private int partition;
	}
	
	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class PartitionSinkFail implements EventSynchConsuemrEvent {
		
		private int partition;
		private Throwable ex;
		
	}

	
	public static enum ACK implements EventSynchConsuemrEvent {
		
		INSTANCE,
		
	}

}