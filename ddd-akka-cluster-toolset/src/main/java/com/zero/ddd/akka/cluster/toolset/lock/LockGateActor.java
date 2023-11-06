package com.zero.ddd.akka.cluster.toolset.lock;

import java.util.HashMap;
import java.util.Map;

import com.zero.ddd.akka.cluster.core.initializer.config.BlockingIODispatcherSelector;
import com.zero.ddd.akka.cluster.toolset.lock.LockGateActor.GateMessage;
import com.zero.ddd.akka.cluster.toolset.lock.LockGateActor.GateMessage.InitLockRef;
import com.zero.ddd.akka.cluster.toolset.lock.client.ClientLockActor;
import com.zero.ddd.akka.cluster.toolset.lock.client.ClientLockActor.ClientLockMessage;
import com.zero.ddd.akka.cluster.toolset.lock.server.ServerLockActor;
import com.zero.ddd.akka.cluster.toolset.lock.server.ServerLockActor.LockCommand;
import com.zero.ddd.akka.cluster.toolset.lock.server.ServerLockActor.LockCommand.STOP_CMD;

import akka.actor.typed.ActorRef;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.Behavior;
import akka.actor.typed.DispatcherSelector;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import akka.cluster.typed.ClusterSingleton;
import akka.cluster.typed.ClusterSingletonSettings;
import akka.cluster.typed.SingletonActor;
import lombok.AllArgsConstructor;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2022-07-26 07:14:34
 * @Desc 些年若许,不负芳华.
 *
 */
public class LockGateActor extends AbstractBehavior<GateMessage> {
	
	public LockGateActor(
			ActorContext<GateMessage> context) {
		super(context);
	}

	private Map<String, ActorRef<ClientLockMessage>> lockPartCache = new HashMap<>();
	
	public static Behavior<GateMessage> gateBehavior(){
		return Behaviors.setup(LockGateActor::new);
	}
	
	@Override
	public Receive<GateMessage> createReceive() {
		return super.newReceiveBuilder()
				.onMessage(
						InitLockRef.class,
						msg -> {
							if (!this.lockPartCache.containsKey(
									msg.lockCenter)) {
								this.lockPartCache.put(
										msg.lockCenter, 
										this.spawnLockActor(
												msg.lockCenter));
								super.getContext().getLog()
								.info(
										"[SingletonActor] SpawnLockServerActor:{}", 
										msg.lockCenter);
							}
							msg.replyTo.tell(
									this.lockPartCache.get(
											msg.lockCenter));
							return Behaviors.same();
						})
				.build();
	}
	
	private ActorRef<ClientLockMessage> spawnLockActor(
			String lockCenter) {
		DispatcherSelector defaultDispatcher = 
				BlockingIODispatcherSelector.defaultDispatcher();
		ActorSystem<Void> system = 
				this.getContext().getSystem();
		// 注册单例的服务端锁管理器
		ActorRef<LockCommand> lockCenterServer = 
				ClusterSingleton.get(
						this.getContext().getSystem())
				.init(
						SingletonActor.of(
								ServerLockActor.create(lockCenter), 
								lockCenter + "-ServerLockActor")
						.withStopMessage(STOP_CMD.INSTANCE)
						.withSettings(
										ClusterSingletonSettings.create(
												system)
										.withRole(lockCenter))
						.withProps(defaultDispatcher));
		super.getContext().getLog()
		.info(
				"lockCenterServer:{}",
				lockCenterServer.path().toSerializationFormat());
		return this.getContext()
				.spawn(
						ClientLockActor.create(
								lockCenter), 
						"lockClient-" + lockCenter,
						defaultDispatcher);
	}

	public static interface GateMessage {
		
		@AllArgsConstructor
		public static class InitLockRef implements GateMessage {
			public final String lockCenter;
			public final ActorRef<ActorRef<ClientLockMessage>> replyTo;
		}
		
	}
	
}

