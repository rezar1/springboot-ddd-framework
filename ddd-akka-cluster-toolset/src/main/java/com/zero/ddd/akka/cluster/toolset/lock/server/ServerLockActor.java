package com.zero.ddd.akka.cluster.toolset.lock.server;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.zero.ddd.akka.cluster.core.msg.ClusterMessage;
import com.zero.ddd.akka.cluster.toolset.lock.ServiceKeyFactory;
import com.zero.ddd.akka.cluster.toolset.lock.client.ClientLockActor.ClientLockMessage;
import com.zero.ddd.akka.cluster.toolset.lock.client.ClientLockActor.ClientLockMessage.AcquireResponse;
import com.zero.ddd.akka.cluster.toolset.lock.client.ClientLockActor.ClientLockMessage.AskLockBusinessInfo;
import com.zero.ddd.akka.cluster.toolset.lock.client.ClientLockActor.ClientLockMessage.InviteToJoinServer;
import com.zero.ddd.akka.cluster.toolset.lock.server.ServerBusinessLockState.CurLockedBusinessInfo;
import com.zero.ddd.akka.cluster.toolset.lock.server.ServerLockActor.LockCommand.ASK_BUSINESS_INFO_TIME_OUT;
import com.zero.ddd.akka.cluster.toolset.lock.server.ServerLockActor.LockCommand.AcquireLockCommand;
import com.zero.ddd.akka.cluster.toolset.lock.server.ServerLockActor.LockCommand.ListingResponse;
import com.zero.ddd.akka.cluster.toolset.lock.server.ServerLockActor.LockCommand.LockClientRegisteCommand;
import com.zero.ddd.akka.cluster.toolset.lock.server.ServerLockActor.LockCommand.LockClientRegisteCommand.LockBusinessInfo;
import com.zero.ddd.akka.cluster.toolset.lock.server.ServerLockActor.LockCommand.ReleaseLockCommand;
import com.zero.ddd.akka.cluster.toolset.lock.server.ServerLockActor.LockCommand.STOP_CMD;
import com.zero.helper.GU;

import akka.actor.Address;
import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.receptionist.Receptionist;
import akka.actor.typed.receptionist.Receptionist.Listing;
import akka.actor.typed.receptionist.ServiceKey;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2022-07-15 03:43:11
 * @Desc 些年若许,不负芳华.
 * 
 */
@Slf4j
public class ServerLockActor {
	
	public static Behavior<LockCommand> create(
			String lockCenter) {
		log.info(
				"Try start single behavior for lockCenter:{}", 
				lockCenter);
		return WaitBuildLockServer.findAllLockClient(lockCenter);
	}
	
	public static class WaitBuildLockServer {
		
		/**
		 * 启动后，等待所有的客户端注册成功，汇总完整所有的锁信息
		 * @param curState
		 * @param businessNeedAssign
		 */
		private static Behavior<LockCommand> findAllLockClient(
				String lockCenter) {
			return Behaviors.setup(context -> {
				ActorRef<Listing> listingResponseAdapter = 
						context.messageAdapter(
								Receptionist.Listing.class, 
								ListingResponse::new);
				ServiceKey<ClientLockMessage> lockClientServiceKey = 
						ServiceKeyFactory.lockClientServiceKey(
										lockCenter);
				context.getSystem().receptionist()
				.tell(
						Receptionist.find(
								lockClientServiceKey, 
								listingResponseAdapter));
				Map<String, ActorRef<ClientLockMessage>> lockClientMap = new HashMap<>();
				ServerBusinessLockState waitBuildState = new ServerBusinessLockState();
				return Behaviors.receive(LockCommand.class)
						.onMessage(
								ListingResponse.class, 
								listing -> {
									List<String> allClientAddress = new ArrayList<>();
									Set<ActorRef<ClientLockMessage>> serviceInstances = 
											listing.listing.getServiceInstances(
													lockClientServiceKey);
									context.getLog().info("lockClientServiceInstances are:{}", serviceInstances);
									if (GU.notNullAndEmpty(serviceInstances)) {
										serviceInstances.forEach(
												client -> {
													context.getLog().info("lock client:{} isAlive?", client, client);
													lockClientMap.put(
															client.path().address().hostPort(),
															client);
													client.tell(
															new AskLockBusinessInfo(
																	context.getSelf()));
										});
										context.getLog().info("allClientAddress are:{}", allClientAddress);
										// 设置规定响应时间
										context.scheduleOnce(
												Duration.ofSeconds(10), 
												context.getSelf(), 
												ASK_BUSINESS_INFO_TIME_OUT.INSTANCE);
										return waitReportBusinessInfo(
												lockCenter,
												lockClientMap,
												waitBuildState);
									}
									// 当前没有加锁客户端
									return active(
											lockCenter, 
											lockClientMap,
											waitBuildState);
								})
						.onMessageEquals(
								STOP_CMD.INSTANCE,
								() -> Behaviors.stopped())
						.build();
			});
			
		}
		
		private static Behavior<LockCommand> waitReportBusinessInfo(
				String lockCenter,
				Map<String, ActorRef<ClientLockMessage>> lockClientMap, 
				ServerBusinessLockState waitBuildState) {
			Set<String> allClientAddress = new HashSet<>(lockClientMap.keySet());
			return Behaviors.receive(LockCommand.class)
					.onMessage(LockClientRegisteCommand.class, req -> {
						if (allClientAddress.remove(req.clientHostPort())) {
							List<LockBusinessInfo> lockBusinessInfoes = 
									req.lockBusinessInfoes;
							if (GU.notNullAndEmpty(lockBusinessInfoes)) {
								// 客户端期望占用的锁列表
								String lockParticipantClient = req.clientHostPort();
								lockBusinessInfoes.stream()
								.forEach(businessInfo -> {
									// 重建业务锁的信息
									waitBuildState.rebuildLockBusinessInfo(
											businessInfo.business, 
											lockParticipantClient,
											businessInfo.duringLock);
								});
							}
						}
						if (allClientAddress.isEmpty()) {
							// 所有的客户端已经整理完毕, 进入正常运行状态
							return active(
									lockCenter, 
									lockClientMap,
									waitBuildState);
						}
						return Behaviors.same();
					})
					.onMessageEquals(
							ASK_BUSINESS_INFO_TIME_OUT.INSTANCE, 
							() -> findAllLockClient(lockCenter))
					.onMessageEquals(
							STOP_CMD.INSTANCE,
							() -> Behaviors.stopped())
					.onAnyMessage(msg -> Behaviors.ignore())
					.build();
		}

		private static Behavior<LockCommand> active(
				String lockCenter, 
				Map<String, ActorRef<ClientLockMessage>> lockClientMap, 
				ServerBusinessLockState activeState) {
			return Behaviors.setup(context -> {
				return new ActiveLockServer(
						lockCenter,
						context,
						activeState,
						lockClientMap).notifyClient();
			});
		}
	}
	
	
	private static class ActiveLockServer {
		
		private ActorContext<LockCommand> context;
		private Map<String, ActorRef<ClientLockMessage>> lockClientMap;
		private ServerBusinessLockState activeState;
		private String lockCenter;
		private ServiceKey<ClientLockMessage> lockClientServiceKey;
		
		public ActiveLockServer(
				String lockCenter,
				ActorContext<LockCommand> context,
				ServerBusinessLockState activeState, 
				Map<String, ActorRef<ClientLockMessage>> lockClientMap) {
			this.context = context;
			this.lockCenter = lockCenter;
			this.activeState = activeState;
			this.lockClientMap = lockClientMap;
			this.subscribeLockClientes();
			context.getLog().info("Start Lock Server:{}", lockCenter);
			context.getLog().info("Lock client are:{}", lockClientMap.keySet());
			context.getLog().info("Lock active state is:{}", activeState);
		}
		
		private void subscribeLockClientes() {
			ActorRef<Listing> listingResponseAdapter = 
					context.messageAdapter(
							Receptionist.Listing.class, 
							ListingResponse::new);
			this.lockClientServiceKey = 
					ServiceKeyFactory.lockClientServiceKey(
									lockCenter);
			context.getSystem().receptionist()
			.tell(
					Receptionist.subscribe(
							lockClientServiceKey, 
							listingResponseAdapter));
		}

		private Behavior<LockCommand> notifyClient() {
			this.notifyBusinessLockParticipantClient(
					this.activeState.getLockMap().keySet());
			// 进行清理，重置和客户端的链接
			this.lockClientMap.clear();
			return running();
		}
		
		private Behavior<LockCommand> running() {
			return Behaviors.receive(LockCommand.class)
					.onMessage(ListingResponse.class,		this::onLockClientChanged)		//锁客户端列表发生变化
					.onMessage(AcquireLockCommand.class, 	this::onLockAcquireCommand)		//客户端尝试获取某个资源的锁
					.onMessage(ReleaseLockCommand.class,	this::onReleaseBusinessLock)	//客户端释放了某个资源的锁
					.onMessageEquals(STOP_CMD.INSTANCE,  	this::lockServerExit)			//当前服务节点退出，进行清理
					.onAnyMessage(this::logIgnoreMessage)
					.build();
		}
		
		public Behavior<LockCommand> logIgnoreMessage(
				LockCommand msg) {
			context.getLog().info(
					"LockCenter:{} ignore msg:{}", 
					this.lockCenter,
					msg);
			return Behaviors.same();
		}
		
		/**
		 * 客户端发生改变
		 * 
		 * @param response
		 * @return
		 */
		public Behavior<LockCommand> onLockClientChanged(
				ListingResponse response) {
			Set<ActorRef<ClientLockMessage>> serviceInstances = 
					response.listing.getServiceInstances(
							this.lockClientServiceKey);
			if (!serviceInstances.isEmpty()) {
				Map<String, ActorRef<ClientLockMessage>> curClientMap = 
						serviceInstances.stream()
						.collect(
								Collectors.toMap(
										client -> client.path().address().hostPort(), 
										client -> client));
				Set<String> curOnlineClientes = new HashSet<>(curClientMap.keySet());
				Set<String> serverHoldClintes = new HashSet<>(lockClientMap.keySet());
				// 获取新加的客户端
				curOnlineClientes.stream()
				.filter(
						clientHostPort -> !serverHoldClintes.contains(clientHostPort))
				.forEach(newJoinClient -> {
					ActorRef<ClientLockMessage> newClientRef = 
							curClientMap.get(newJoinClient);
					this.lockClientMap.put(
							newJoinClient, 
							newClientRef);
					// 发送邀请
					newClientRef.tell(
							new InviteToJoinServer(
									context.getSelf()));
				});
				// 断线的客户端
				Set<String> exitedLockClientes = 
						serverHoldClintes.stream()
						.filter(
								clientHostPort -> !curOnlineClientes.contains(clientHostPort))
						.peek(this.lockClientMap::remove)
						.collect(Collectors.toSet());
				this.lockClientExited(exitedLockClientes);
			} else {
				// 所有的客户端都断开了, 一般不会
				this.lockClientExited(
						this.lockClientMap.keySet());
			}
			return Behaviors.same();
		}
		
		private void lockClientExited(
				Set<String> exitedLockClientes) {
			if (GU.isNullOrEmpty(exitedLockClientes)) {
				return;
			}
			Set<String> exitedClientHoldedBusinesses = new HashSet<>();
			exitedLockClientes.stream()
			.forEach(client -> {
				List<String> lockedBusinessByClient = 
						this.activeState.lockedByClient(client);
				if (GU.notNullAndEmpty(lockedBusinessByClient)) {
					exitedClientHoldedBusinesses.addAll(
							lockedBusinessByClient);
				}
			});
			this.activeState.releaseClientes(
					exitedLockClientes);
			if (GU.notNullAndEmpty(
					exitedClientHoldedBusinesses)) {
				this.notifyBusinessLockParticipantClient(
						exitedClientHoldedBusinesses);
			}
		}

		private Behavior<LockCommand> onLockAcquireCommand(
				AcquireLockCommand req) {
			String clientHostPort = req.clientHostPort();
			if (!this.lockClientMap.containsKey(
					clientHostPort)) {
				context.getLog().warn(
						"lock client not registe before:{}",
						clientHostPort);
				return Behaviors.same();
			}
			ActorRef<ClientLockMessage> actorRef = 
					this.lockClientMap.get(
							clientHostPort);
			// 已锁定成功，直接返回true
			String lockParticipantClient = clientHostPort;
			if (activeState.hasLockedByClient(
					req.lockBusiness, 
					lockParticipantClient)) {
				actorRef.tell(
						new AcquireResponse(
								true, 
								req.lockBusiness,
								activeState.lockVersion(
										req.lockBusiness)));
			} else {
				activeState.attemptLockBusiness(
						req.lockBusiness,
						lockParticipantClient);
				actorRef.tell(
						new AcquireResponse(
								this.activeState.hasLockedByClient(
										req.lockBusiness, 
										lockParticipantClient),
								req.lockBusiness,
								activeState.lockVersion(
										req.lockBusiness)));
			}
			return Behaviors.same();
		}

		private Behavior<LockCommand> onReleaseBusinessLock(
				ReleaseLockCommand req) {
			// 某个客户端释放锁资源
			String lockParticipantClient = req.clientHostPort();
			if (activeState.hasLockedByClient(
					req.lockBusiness,
					lockParticipantClient)) {
				activeState.releaseBusinessLock(
						req.lockBusiness,
						lockParticipantClient);
				this.notifyBusinessLockParticipantClient(
						Arrays.asList(req.lockBusiness));
			}
			return Behaviors.same();
		}
		
		private Behavior<LockCommand> lockServerExit() {
			this.context.getLog()
			.info("Exited Lock Server:{}", this.lockCenter);
			return Behaviors.stopped();
		}
		
		/**
		 * 通知当前已注册的客户端某些锁已被释放
		 * 
		 * @param curState
		 * @param businessNeedAssign
		 */
		private void notifyBusinessLockParticipantClient(
				Collection<String> businessNeedAssignOwner) {
			businessNeedAssignOwner
			.forEach(
					lockBusiness -> {
						// 通知某个客户端获取到了锁
						CurLockedBusinessInfo curLockedBusinessInfo = 
								this.activeState.curLockBusinessClient(
										lockBusiness);
						if (curLockedBusinessInfo == null) {
							return;
						}
						this.lockClientMap.get(
								curLockedBusinessInfo.lockedBy)
						.tell(
								new AcquireResponse(
										true,
										lockBusiness,
										curLockedBusinessInfo.lockVersion));
					});
		}
		
	}
	
	
	
	public static interface LockCommand extends ClusterMessage {
		
		public static enum STOP_CMD implements LockCommand {
			INSTANCE
		}
		public static enum ASK_BUSINESS_INFO_TIME_OUT implements LockCommand {
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
			public String clientAddress;
			public Long stopedTime;
			
			public LockClientStoped(
					String clientAddress) {
				this.clientAddress = clientAddress;
			}
		}
		
		@NoArgsConstructor
		@AllArgsConstructor
		public static class ReleaseLockCommand implements LockCommand {
			public String lockBusiness;
			public ActorRef<ClientLockMessage> clientLockRef;
			
			public ReleaseLockCommand(
					String lockBusiness,
					ActorRef<ClientLockMessage> clientLockRef) {
				this.lockBusiness = lockBusiness;
				this.clientLockRef = clientLockRef;
			}
			
			public String clientHostPort() {
				return clientLockRef.path().address().hostPort();
			}

			public long curTime = System.nanoTime();
		}
		
		public static class LockClientRegisteCommand implements LockCommand {
			
			public String clientHostPort() {
				return this.clientLockRef.path().address().hostPort();
			}
			
			public boolean firstRegiste;
			public List<LockBusinessInfo> lockBusinessInfoes;
			public ActorRef<ClientLockMessage> clientLockRef;
			
			public LockClientRegisteCommand() {
				super();
			}

			public LockClientRegisteCommand(boolean firstRegiste, List<LockBusinessInfo> lockBusinessInfoes,
					ActorRef<ClientLockMessage> clientLockRef) {
				super();
				this.firstRegiste = firstRegiste;
				this.lockBusinessInfoes = lockBusinessInfoes;
				this.clientLockRef = clientLockRef;
			}

			@NoArgsConstructor
			@AllArgsConstructor
			public static class LockBusinessInfo {
				public String business;
				public boolean duringLock;
			}
			
		}
		
		@NoArgsConstructor
		@AllArgsConstructor
		public static class AcquireLockCommand implements LockCommand {
			public String lockBusiness;
			public ActorRef<ClientLockMessage> clientLockRef;
			
			public String clientHostPort() {
				return clientLockRef.path().address().hostPort();
			}
		}
	}
	
}