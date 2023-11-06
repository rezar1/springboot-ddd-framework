package com.zero.ddd.akka.cluster.core.toolset.lock.server;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.zero.ddd.akka.cluster.core.msg.ClusterMessage;
import com.zero.ddd.akka.cluster.core.toolset.lock.ServiceKeyFactory;
import com.zero.ddd.akka.cluster.core.toolset.lock.client.ClientLockActor.ClientLockMessage;
import com.zero.ddd.akka.cluster.core.toolset.lock.client.ClientLockActor.ClientLockMessage.AcquireResponse;
import com.zero.ddd.akka.cluster.core.toolset.lock.client.ClientLockActor.ClientLockMessage.BusinessLockReleased;
import com.zero.ddd.akka.cluster.core.toolset.lock.server.BusinessLockState.LockParticipantClient;
import com.zero.ddd.akka.cluster.core.toolset.lock.server.ServerLockActor.LockCommand;
import com.zero.ddd.akka.cluster.core.toolset.lock.server.ServerLockActor.LockCommand.ListingResponse;
import com.zero.ddd.akka.cluster.core.toolset.lock.server.ServerLockActor.LockCommand.LockAcquireCommand;
import com.zero.ddd.akka.cluster.core.toolset.lock.server.ServerLockActor.LockCommand.LockClientRegisteCommand;
import com.zero.ddd.akka.cluster.core.toolset.lock.server.ServerLockActor.LockCommand.LockClientStoped;
import com.zero.ddd.akka.cluster.core.toolset.lock.server.ServerLockActor.LockCommand.NeedCheckParticipantClientCmd;
import com.zero.ddd.akka.cluster.core.toolset.lock.server.ServerLockActor.LockCommand.ReleaseLockCommand;
import com.zero.ddd.akka.cluster.core.toolset.lock.server.ServerLockActor.LockCommand.STOP_CMD;
import com.zero.ddd.akka.cluster.core.toolset.lock.server.ServerLockActor.LockEvent;
import com.zero.ddd.akka.cluster.core.toolset.lock.server.ServerLockActor.LockEvent.ClientTryLockBusinessEvent;
import com.zero.ddd.akka.cluster.core.toolset.lock.server.ServerLockActor.LockEvent.ResetAllBusinessLockByClient;
import com.zero.ddd.akka.cluster.core.toolset.lock.server.ServerLockActor.LockEvent.ResetAllBusinessLockByClientes;
import com.zero.ddd.akka.cluster.core.toolset.lock.server.ServerLockActor.LockEvent.ResetLockByClientAndBusiness;
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
public class ServerLockActor extends EventSourcedBehavior<LockCommand, LockEvent, BusinessLockState> {
	
	private ActorContext<LockCommand> context;
	
	private Map<Address, ActorRef<ClientLockMessage>> lockClientMap = new HashMap<>();

	private ServiceKey<ClientLockMessage> lockClientServiceKey;
	
	public static Behavior<LockCommand> create(
			String lockPart) {
		return Behaviors.setup(
				context -> new ServerLockActor(lockPart, context));
	}
	
	public ServerLockActor(
			String lockPart,
			ActorContext<LockCommand> context) {
		super(PersistenceId.of("ClusterLock", lockPart, "-"));
		this.context = context;
		this.subscribeAllLockClient(lockPart);
		context.getLog().info("Start Lock Context:{}", lockPart);
	}
	
	private void subscribeAllLockClient(
			String lockPart) {
		this.lockClientServiceKey = 
				ServiceKeyFactory.lockClientServiceKey(
						lockPart);
		ActorRef<Listing> listingResponseAdapter = 
				context.messageAdapter(
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
	public CommandHandler<LockCommand, LockEvent, BusinessLockState> commandHandler() {
		return super.newCommandHandlerBuilder()
				.forAnyState()
				.onCommand(STOP_CMD.class, (state, req) -> Effect().stop())
//				.onCommand(
//						ListingResponse.class, 
//						(state, req) -> {
//							Set<ActorRef<ClientLockMessage>> allServiceInstances = 
//									req.listing.getAllServiceInstances(
//											this.lockClientServiceKey);
//							
//							return Effect().none();
//						})
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
											new ResetAllBusinessLockByClientes(
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
										new ResetAllBusinessLockByClient(
												lockParticipantClient))
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
							// 某个客户端释放锁资源
							LockParticipantClient lockParticipantClient = 
									new LockParticipantClient(
											req.clientAddress);
							if (state.hasLockedByClient(
									req.lockBusiness,
									lockParticipantClient)) {
								return Effect().persist(
										new ResetLockByClientAndBusiness(
												lockParticipantClient,
												req.lockBusiness))
										.thenRun(newState -> {
											this.notifyBusinessLockParticipantClient(
													newState, 
													Arrays.asList(req.lockBusiness));
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
											new ResetAllBusinessLockByClient(
													lockParticipantClient))
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
												newSate -> new AcquireResponse(true, req.lockBusiness));
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
			BusinessLockState curState, 
			List<String> businessLockedByTargetClient) {
		businessLockedByTargetClient.forEach(
				lockedBusiness -> {
					List<Address> clientAddress = 
							curState.listWaitAcquireLockClient(
									lockedBusiness);
					if (GU.notNullAndEmpty(clientAddress)) {
						BusinessLockReleased releasedBusiness = 
								new BusinessLockReleased(
										lockedBusiness);
						clientAddress.stream()
						.map(address -> this.lockClientMap.get(address))
						.filter(Objects::nonNull)
						.forEach(ref -> {
							ref.tell(releasedBusiness);
							context.getLog().info("notify client:{} business:{} has released", ref, lockedBusiness);
						});
					}
				});
	}
	
	@Override
	public BusinessLockState emptyState() {
		return new BusinessLockState();
	}
	
	@Override
	public SignalHandler<BusinessLockState> signalHandler() {
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
	public EventHandler<BusinessLockState, LockEvent> eventHandler() {
		return super.newEventHandlerBuilder()
				.forAnyState()
				.onEvent(ClientTryLockBusinessEvent.class,		(state, event) -> state.attemptLockBusiness(event.business, event.client))
				.onEvent(ResetAllBusinessLockByClient.class, 	(state, event) -> state.releaseClient(event.client))
				.onEvent(ResetAllBusinessLockByClientes.class, 	(state, event) -> state.releaseClientes(event.clientes))
				.onEvent(ResetLockByClientAndBusiness.class, 	(state, event) -> state.releaseBusinessLock(event.business, event.client))
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
				return Collections.unmodifiableSet(this.curParticipantClientAddressSet);
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
		public static class ResetAllBusinessLockByClient implements LockEvent {
			public LockParticipantClient client;
		}
		
		@NoArgsConstructor
		@AllArgsConstructor
		public static class ResetAllBusinessLockByClientes implements LockEvent {
			public Set<LockParticipantClient> clientes;
		}
		
		@NoArgsConstructor
		@AllArgsConstructor
		public static class ResetLockByClientAndBusiness implements LockEvent {
			public LockParticipantClient client;
			public String business;
		}
		
	}
}