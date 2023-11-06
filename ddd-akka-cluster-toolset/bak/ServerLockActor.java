package com.zero.ddd.akka.cluster.toolset.lock.server;

import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.Sets;
import com.zero.ddd.akka.cluster.core.msg.ClusterMessage;
import com.zero.ddd.akka.cluster.toolset.lock.ServiceKeyFactory;
import com.zero.ddd.akka.cluster.toolset.lock.client.ClientLockActor.ClientLockMessage;
import com.zero.ddd.akka.cluster.toolset.lock.client.ClientLockActor.ClientLockMessage.AcquireResponse;
import com.zero.ddd.akka.cluster.toolset.lock.server.ServerBusinessLockState.LockParticipantClient;
import com.zero.ddd.akka.cluster.toolset.lock.server.ServerLockActor.LockCommand;
import com.zero.ddd.akka.cluster.toolset.lock.server.ServerLockActor.LockCommand.ListingResponse;
import com.zero.ddd.akka.cluster.toolset.lock.server.ServerLockActor.LockCommand.LockAcquireCommand;
import com.zero.ddd.akka.cluster.toolset.lock.server.ServerLockActor.LockCommand.LockClientRegisteCommand;
import com.zero.ddd.akka.cluster.toolset.lock.server.ServerLockActor.LockCommand.LockClientStoped;
import com.zero.ddd.akka.cluster.toolset.lock.server.ServerLockActor.LockCommand.NeedCheckParticipantClientCmd;
import com.zero.ddd.akka.cluster.toolset.lock.server.ServerLockActor.LockCommand.ReleaseLockCommand;
import com.zero.ddd.akka.cluster.toolset.lock.server.ServerLockActor.LockCommand.STOP_CMD;
import com.zero.ddd.akka.cluster.toolset.lock.server.ServerLockActor.LockEvent;
import com.zero.ddd.akka.cluster.toolset.lock.server.ServerLockActor.LockEvent.ClearAllBusinessLockByClientes;
import com.zero.ddd.akka.cluster.toolset.lock.server.ServerLockActor.LockEvent.ClientReleaseBusinessLock;
import com.zero.ddd.akka.cluster.toolset.lock.server.ServerLockActor.LockEvent.ClientTryLockBusinessEvent;
import com.zero.helper.GU;

import akka.actor.Address;
import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.receptionist.Receptionist;
import akka.actor.typed.receptionist.Receptionist.Listing;
import akka.actor.typed.receptionist.ServiceKey;
import akka.persistence.typed.PersistenceId;
import akka.persistence.typed.RecoveryCompleted;
import akka.persistence.typed.javadsl.CommandHandler;
import akka.persistence.typed.javadsl.EventHandler;
import akka.persistence.typed.javadsl.EventSourcedBehavior;
import akka.persistence.typed.javadsl.SignalHandler;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2022-07-15 03:43:11
 * @Desc 些年若许,不负芳华.
 * 
 */
public class ServerLockActor extends EventSourcedBehavior<LockCommand, LockEvent, ServerBusinessLockState> {
	
	private ActorContext<LockCommand> context;
	
	private Map<Address, ActorRef<ClientLockMessage>> lockClientMap = new HashMap<>();

	private ServiceKey<LockCommand> lockClientServiceKey;
	
	public static Behavior<LockCommand> create(
			String lockCenter) {
		return Behaviors.setup(
				context -> new ServerLockActor(lockCenter, context));
	}
	
	public ServerLockActor(
			String lockCenter,
			ActorContext<LockCommand> context) {
		super(PersistenceId.of("ClusterLock", GU.isNullOrEmpty(lockCenter) ? "common" : lockCenter, "-"));
		this.context = context;
		this.registeSelf(lockCenter);
		context.getLog().info("Start Lock Context:{}", lockCenter);
	}
	
	private void registeSelf(
			String lockCenter) {
		context
        .getSystem()
        .receptionist()
        .tell(
        		Receptionist.register(
        				ServiceKeyFactory.lockServerServiceKey(
        						lockCenter),
        				context.getSelf()));
	}
	
	@SuppressWarnings("unused")
	private void subscribeAllLockClient(
			String lockPart) {
		this.lockClientServiceKey = 
				ServiceKeyFactory.lockServerServiceKey(
						lockPart);
		ActorRef<Listing> listingResponseAdapter = 
				this.context.messageAdapter(
						Receptionist.Listing.class, 
						ListingResponse::new);
		context
        .getSystem()
        .receptionist()
        .tell(
        		Receptionist.subscribe(
        				this.lockClientServiceKey,
        				listingResponseAdapter));
	}

	@Override
	public CommandHandler<LockCommand, LockEvent, ServerBusinessLockState> commandHandler() {
		return super.newCommandHandlerBuilder()
				.forAnyState()
				.onCommand(STOP_CMD.class, (state, req) -> Effect().stop())
				.onCommand(
						NeedCheckParticipantClientCmd.class, 
						(state, req) -> {
							// 恢复后，等待一段时间，移除这段时间内还未注册上的锁客户端，并释放这些客户端占用的锁
							Set<Address> cur = 
									req.curParticipantClientAddressSet();
							if (GU.notNullAndEmpty(cur)
									&& cur.removeAll(this.lockClientMap.keySet())) {
								Map<LockParticipantClient, List<String>> eachAddressJoinedBusiness = new HashMap<>();
								cur.stream()
								.forEach(address -> {
									LockParticipantClient client = 
											new LockParticipantClient(address);
									List<String> lockedByClient = 
											state.lockedByClient(client);
									if (GU.notNullAndEmpty(lockedByClient)) {
										eachAddressJoinedBusiness.put(
												client, 
												lockedByClient);
									}
								});
								if (GU.notNullAndEmpty(
										eachAddressJoinedBusiness)) {
									return Effect().persist(
											new ClearAllBusinessLockByClientes(
													eachAddressJoinedBusiness.keySet()))
											.thenRun(newState -> {
												eachAddressJoinedBusiness
												.forEach(
														(client, lockedByClient) -> {
															this.notifyBusinessLockParticipantClient(
																	newState, 
																	lockedByClient);
														});
											});
								}
							}
							return Effect().none();
						})
				.onCommand(
						LockClientStoped.class, 
						(state, req) -> {
							// 是不是需要等待一段时间?
//							if (req.stopedTime == null) {
//								context.scheduleOnce(
//										Duration.ofSeconds(180), 
//										context.getSelf(), 
//										new LockClientStoped(
//												req.clientAddress,
//												System.currentTimeMillis()));
//								return Effect().none();
//							}
							// 监控到某个节点断链后
							LockParticipantClient lockParticipantClient = 
									new LockParticipantClient(
											req.clientAddress);
							List<String> lockedByClient = 
									state.lockedByClient(
											new LockParticipantClient(
													req.clientAddress));
							if (GU.notNullAndEmpty(lockedByClient)) {
								return Effect().persist(
										new ClearAllBusinessLockByClientes(
												Sets.newHashSet(lockParticipantClient)))
										.thenRun(newState -> {
											this.notifyBusinessLockParticipantClient(
													newState, 
													lockedByClient);
										});
							}
							this.lockClientMap.remove(req.clientAddress);
							return Effect().none();
						})
				.onCommand(
						ReleaseLockCommand.class, 
						(state, req) -> {
							long transTime = System.nanoTime() - req.curTime;
							// 某个客户端释放锁资源
							LockParticipantClient lockParticipantClient = 
									new LockParticipantClient(
											req.clientAddress);
							if (state.hasLockedByClient(
									req.lockBusiness,
									lockParticipantClient)) {
								return Effect().persist(
										new ClientReleaseBusinessLock(
												lockParticipantClient,
												req.lockBusiness))
										.thenRun(newState -> {
											newState.curLockBusinessClient(req.lockBusiness);
											this.notifyBusinessLockParticipantClient(
													newState, 
													Arrays.asList(req.lockBusiness));
											context.getLog().info("transTime:{} total end time:{}", transTime, (System.nanoTime() - req.curTime));
										});
							}
							return Effect().none();
						})
				.onCommand(
						LockClientRegisteCommand.class, 
						(state, req) -> {
							// 添加断线监控
							this.startWatchAndCache(
									req.clientAddress(), 
									req.clientLockRef);
							// 某个节点断线重连后, 重新表明自己是新加入的
							if (req.firstRegiste) {
								LockParticipantClient lockParticipantClient = 
										new LockParticipantClient(
												req.clientAddress());
								List<String> lockedByClient = 
										state.lockedByClient(
												new LockParticipantClient(
														req.clientAddress()));
								if (GU.notNullAndEmpty(lockedByClient)) {
									return Effect().persist(
											new ClearAllBusinessLockByClientes(
													Sets.newHashSet(lockParticipantClient)))
											.thenRun(newState -> {
												this.notifyBusinessLockParticipantClient(
														newState, 
														lockedByClient);
											});
								}
							}
							return Effect().none();
						})
				.onCommand(
						LockAcquireCommand.class, 
						(state, req) -> {
							if (!this.lockClientMap.containsKey(
									req.address)) {
								context.getLog().warn("lock client not registe before:{}", req.address);
								return Effect().none();
							}
							ActorRef<ClientLockMessage> actorRef = 
									this.lockClientMap.get(
											req.address);
							// 已锁定成功，直接返回true
							LockParticipantClient lockParticipantClient = 
									new LockParticipantClient(
											req.address);
							if (state.hasLockedByClient(
									req.lockBusiness, 
									lockParticipantClient)) {
								return Effect().none()
										.thenReply(
												actorRef, 
												newSate -> new AcquireResponse(
														true, 
														req.lockBusiness));
							}
							return 
									Effect().persist(
											new ClientTryLockBusinessEvent(
													req.lockBusiness,
													lockParticipantClient))
									.thenReply(
											actorRef, 
											newSate -> {
												return new AcquireResponse(
														newSate.hasLockedByClient(
																req.lockBusiness, 
																lockParticipantClient),
														req.lockBusiness);
											});
						})
				.build();
	}
	
	// 缓存锁的客户端/开启监控
	private void startWatchAndCache(
			Address clientAddress, 
			ActorRef<ClientLockMessage> clientLockRef) {
		this.lockClientMap.put(
				clientAddress, 
				clientLockRef);
		context.watchWith(
				clientLockRef,
				new LockClientStoped(
						clientAddress));
	}

	/**
	 * 通知当前已注册的客户端某些锁已被释放
	 * 
	 * @param curState
	 * @param businessLockedByTargetClient
	 */
	private void notifyBusinessLockParticipantClient(
			ServerBusinessLockState curState, 
			List<String> businessLockedByTargetClient) {
		businessLockedByTargetClient.forEach(
				lockedBusiness -> {
					// 通知某个客户端获取到了锁
					LockParticipantClient curLockBusinessClient = 
							curState.curLockBusinessClient(lockedBusiness);
					this.lockClientMap.get(
							curLockBusinessClient.getHostAddress())
					.tell(
							new AcquireResponse(true, lockedBusiness));
//					List<Address> clientAddress = 
//							curState.listWaitAcquireLockClient(
//									lockedBusiness);
//					if (GU.notNullAndEmpty(clientAddress)) {
//						BusinessLockReleased releasedBusiness = 
//								new BusinessLockReleased(
//										lockedBusiness);
//						clientAddress.stream()
//						.map(address -> this.lockClientMap.get(address))
//						.filter(Objects::nonNull)
//						.forEach(ref -> {
//							ref.tell(releasedBusiness);
//							context.getLog().info("notify client:{} business:{} has released", ref, lockedBusiness);
//						});
//					}
				});
	}
	
	@Override
	public ServerBusinessLockState emptyState() {
		return new ServerBusinessLockState();
	}
	
	@Override
	public SignalHandler<ServerBusinessLockState> signalHandler() {
		return newSignalHandlerBuilder()
				.onSignal(
						RecoveryCompleted.instance(), 
						state -> {
							// 启动后，等待30s，超过30s还未注册的客户端，视为已掉线
							context.scheduleOnce(
									Duration.ofSeconds(30), 
									context.getSelf(), 
									new NeedCheckParticipantClientCmd(
											state.allLockParticipantClientAddress()));
							})
				.build();
	}

	@Override
	public EventHandler<ServerBusinessLockState, LockEvent> eventHandler() {
		return super.newEventHandlerBuilder()
				.forAnyState()
				.onEvent(ClientTryLockBusinessEvent.class,		(state, event) -> state.attemptLockBusiness(event.business, event.client))
				.onEvent(ClearAllBusinessLockByClientes.class, 	(state, event) -> state.releaseClientes(event.clientes))
				.onEvent(ClientReleaseBusinessLock.class, 	(state, event) -> state.releaseBusinessLock(event.business, event.client))
				.build();
	}
	
	public static interface LockCommand extends ClusterMessage {
		
		public static enum STOP_CMD implements LockCommand{
			INSTANCE
		}
		
		public static class ListingResponse implements LockCommand {
			
			final Receptionist.Listing listing;

			private ListingResponse(
					Receptionist.Listing listing) {
				this.listing = listing;
			}
		}
		
		@AllArgsConstructor
		public static class NeedCheckParticipantClientCmd implements LockCommand {
			
			public Set<Address> curParticipantClientAddressSet() {
				return new HashSet<>(this.curParticipantClientAddressSet);
			}

			public final Set<Address> curParticipantClientAddressSet;
		}
		
		@NoArgsConstructor
		@AllArgsConstructor
		public static class LockClientStoped implements LockCommand {
			public Address clientAddress;
			public Long stopedTime;
			
			public LockClientStoped(
					Address clientAddress) {
				this.clientAddress = clientAddress;
			}
		}
		
		@NoArgsConstructor
		@AllArgsConstructor
		public static class ReleaseLockCommand implements LockCommand {
			public Address clientAddress;
			public String lockBusiness;
			
			
			public ReleaseLockCommand(Address clientAddress, String lockBusiness) {
				this.clientAddress = clientAddress;
				this.lockBusiness = lockBusiness;
			}

			public long curTime = System.nanoTime();
		}
		
		@NoArgsConstructor
		@AllArgsConstructor
		public static class LockClientRegisteCommand implements LockCommand {
			
			public Address clientAddress() {
				return clientLockRef.path().address();
			}
			
			public boolean firstRegiste;
			public ActorRef<ClientLockMessage> clientLockRef;
		}
		
		@NoArgsConstructor
		@AllArgsConstructor
		public static class LockAcquireCommand implements LockCommand {
			
			public String lockBusiness;
			public Address address;
		}
	}
	
	public static interface LockEvent extends ClusterMessage {
		
		@NoArgsConstructor
		@AllArgsConstructor
		public static class ClientTryLockBusinessEvent implements LockEvent {
			public String business;
			public LockParticipantClient client;
		}
		
		@NoArgsConstructor
		@AllArgsConstructor
		public static class ClearAllBusinessLockByClientes implements LockEvent {
			public Set<LockParticipantClient> clientes;
		}
		
		@NoArgsConstructor
		@AllArgsConstructor
		public static class ClientReleaseBusinessLock implements LockEvent {
			public LockParticipantClient client;
			public String business;
		}
		
	}
}