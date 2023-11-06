package com.zero.ddd.akka.cluster.toolset.lock.client;

import java.util.ArrayList;
import java.util.List;

import com.google.common.cache.LoadingCache;
import com.zero.ddd.akka.cluster.core.msg.ClusterMessage;
import com.zero.ddd.akka.cluster.toolset.lock.ServiceKeyFactory;
import com.zero.ddd.akka.cluster.toolset.lock.client.ClientLockActor.ClientLockMessage;
import com.zero.ddd.akka.cluster.toolset.lock.client.ClientLockActor.ClientLockMessage.AcquireResponse;
import com.zero.ddd.akka.cluster.toolset.lock.client.ClientLockActor.ClientLockMessage.AskLockBusinessInfo;
import com.zero.ddd.akka.cluster.toolset.lock.client.ClientLockActor.ClientLockMessage.BusinessAcquireLockRequest;
import com.zero.ddd.akka.cluster.toolset.lock.client.ClientLockActor.ClientLockMessage.InitLocalClusterLock;
import com.zero.ddd.akka.cluster.toolset.lock.client.ClientLockActor.ClientLockMessage.InviteToJoinServer;
import com.zero.ddd.akka.cluster.toolset.lock.client.ClientLockActor.ClientLockMessage.LockServerStoped;
import com.zero.ddd.akka.cluster.toolset.lock.client.ClientLockActor.ClientLockMessage.ThreadReleasedLock;
import com.zero.ddd.akka.cluster.toolset.lock.server.ServerLockActor.LockCommand;
import com.zero.ddd.akka.cluster.toolset.lock.server.ServerLockActor.LockCommand.AcquireLockCommand;
import com.zero.ddd.akka.cluster.toolset.lock.server.ServerLockActor.LockCommand.LockClientRegisteCommand;
import com.zero.ddd.akka.cluster.toolset.lock.server.ServerLockActor.LockCommand.LockClientRegisteCommand.LockBusinessInfo;
import com.zero.ddd.akka.cluster.toolset.lock.server.ServerLockActor.LockCommand.ReleaseLockCommand;
import com.zero.ddd.core.toolsets.lock.ClusterLockAcquireParam;
import com.zero.helper.SimpleCacheBuilder;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.PostStop;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import akka.actor.typed.javadsl.StashBuffer;
import akka.actor.typed.receptionist.Receptionist;
import akka.cluster.Cluster;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2022-07-18 04:08:24
 * @Desc 些年若许,不负芳华.
 *
 */
public class ClientLockActor extends AbstractBehavior<ClientLockMessage> {
	
	private boolean notFirst;
	private String clientAddress;
	private ActorRef<ClientLockMessage> selfClientRef;
	private StashBuffer<ClientLockMessage> buffer;
	private ActorRef<LockCommand> server;
	
	private LoadingCache<ClusterLockAcquireParam, InterProcessLock> lockDataCache = 
			SimpleCacheBuilder.instance(this::initInterProcessLock);
	private String lockCenter;
	
	public static Behavior<ClientLockMessage> create(
			String lockCenter){
		return Behaviors.setup(
				context -> {
					return Behaviors.withStash(
							200, 
							buffer -> new ClientLockActor(
									lockCenter,
									buffer,
									context));
				});
	}

	public ClientLockActor(
			String lockCenter,
			StashBuffer<ClientLockMessage> buffer,
			ActorContext<ClientLockMessage> context) {
		super(context);
		this.lockCenter = lockCenter;
		this.clientAddress = 
				Cluster.get(context.getSystem()).selfAddress().hostPort();
		this.selfClientRef = context.getSelf();
		this.buffer = buffer;
		this.registeSelfAsLockClient(lockCenter);
		context.getLog().info(
				"Start Lock Client:{} with address:{}", 
				lockCenter, 
				this.clientAddress);
	}
	
	// 注册自己作为一个锁的客户端
	private void registeSelfAsLockClient(
			String lockCenter) {
		getContext()
		.getSystem()
		.receptionist()
		.tell(
				Receptionist.register(
						ServiceKeyFactory.lockClientServiceKey(
								lockCenter),
						getContext().getSelf()));
	}
	
	private InterProcessLock initInterProcessLock(
			ClusterLockAcquireParam param) {
		// 申请获取某个锁
		this.selfClientRef.tell(
				new BusinessAcquireLockRequest(
						param.getLockBusiness()));
		return new InterProcessLock(
				param,
				() -> {
					// 通知远程客户端释放了当前锁
					this.selfClientRef.tell(
							new ThreadReleasedLock(
									param.getLockBusiness()));
				});
	}
	
	@Override
	public Receive<ClientLockMessage> createReceive() {
		// 初次启动等待注册为锁客户端
		return waitInitRegiste();
	}

	private Receive<ClientLockMessage> waitInitRegiste() {
		return super.newReceiveBuilder()
				.onMessage(InviteToJoinServer.class, 		this::onInviteToJoinServer)		//LockCenter推送自身ActorRef给当前节点
				.onMessage(AskLockBusinessInfo.class,		this::onAskLockBusinessInfo)	//LockCenter询问当前节点拥有的锁资源信息
				.onAnyMessage(this::stashMsg)
				.build();
	}
	
	private Behavior<ClientLockMessage> stashMsg(
			ClientLockMessage msg) {
		this.buffer.stash(msg);
		return Behaviors.same();
	}
	
	private Behavior<ClientLockMessage> normalState() {
		return super.newReceiveBuilder()
				.onMessage(LockServerStoped.class, 				this::onLockServerChanaged)				//LockCenter服务端节点下线
				.onMessage(ThreadReleasedLock.class, 			this::onThreadReleasedLock)				//本地线程释放了某个资源的锁
				.onMessage(AcquireResponse.class, 				this::onAcquireResponse)				//服务端推送本地获取到了某个资源的锁
				.onMessage(InitLocalClusterLock.class,			this::initLocalClusterLock)				//本地线程初始化InterProcessLock实例
				.onMessage(BusinessAcquireLockRequest.class, 	this::sendBusinessLockAcquireRequest)	//注册当前节点到某个资源的等待队列中
				.onSignal(PostStop.class, 						this::onPostStop)						//本地节点退出，进行清理
				.build();
	}
	
	private Behavior<ClientLockMessage> onPostStop(
			PostStop msg) {
		super.getContext().getSystem().receptionist()
		.tell(
				Receptionist.deregister(
						ServiceKeyFactory.lockClientServiceKey(
								this.lockCenter),
						getContext().getSelf()));
		this.lockDataCache.asMap()
		.entrySet()
		.forEach(entry -> {
			entry.getValue().clientServerExit();
			this.getContext().getLog().info("business:{} exited wait lock", entry.getKey());
		});
		return Behaviors.stopped();
	}
	
	// 初次启动之后，服务端发送邀请加入该lockCenter服务中, 然后转入正常运行状态
	private Behavior<ClientLockMessage> onInviteToJoinServer(
			InviteToJoinServer msg) {
		this.server = 
				msg.serverRef;
		super.getContext().watchWith(
				msg.serverRef,
				new LockServerStoped(
						msg.serverRef));
		return this.buffer.unstashAll(
				this.normalState());
	}
	
	private Behavior<ClientLockMessage> onAskLockBusinessInfo(
			AskLockBusinessInfo msg) {
		this.reportClientLockInfo(
				msg.replyTo);
		return Behaviors.same();
	}
	
	private Behavior<ClientLockMessage> onThreadReleasedLock(
			ThreadReleasedLock msg) {
		this.server.tell(
				new ReleaseLockCommand(
						msg.lockBusiness,
						this.selfClientRef));
		return Behaviors.same();
	}
	
	private Behavior<ClientLockMessage> sendBusinessLockAcquireRequest(
			BusinessAcquireLockRequest msg) {
		this.server.tell(
				new AcquireLockCommand(
						msg.lockBusiness,
						this.selfClientRef));
		return Behaviors.same();
	}
	
	private Behavior<ClientLockMessage> onAcquireResponse(
			AcquireResponse msg) {
		if (super.getContext().getLog().isDebugEnabled()) {
			super.getContext().getLog()
			.info(
					"business:{} acquire resp:{} version:{}", 
					msg.business,
					msg.lockSucc,
					msg.version);
		}
		if (msg.lockSucc) {
			InterProcessLock processLock = 
					this.lockDataCache.getUnchecked(
							ClusterLockAcquireParam.builder()
							.lockCenter(lockCenter)
							.lockBusiness(msg.business)
							.build());
			processLock.clientServerAcquiredLock();
		}
		return Behaviors.same();
	}
	
	// 当前LockServer节点下线，等待新的服务节点上线
	private Behavior<ClientLockMessage> onLockServerChanaged(
			LockServerStoped serverStoped) {
		this.server = null;
		return this.waitInitRegiste();
	}

	private void reportClientLockInfo(
			ActorRef<LockCommand> server) {
		boolean isFirstRegiste = 
				(this.notFirst = (!this.notFirst ? true : false));
		List<LockBusinessInfo> lockBusinessInfoes = new ArrayList<>();
		this.lockDataCache.asMap().entrySet().stream()
		.forEach(entry -> {
			lockBusinessInfoes.add(
					new LockBusinessInfo(
							entry.getKey().getLockBusiness(),
							entry.getValue().duringLock()));
		});
		server.tell(
				new LockClientRegisteCommand(
						isFirstRegiste,
						lockBusinessInfoes,
						this.selfClientRef));
	}

	/**
	 * 客户端尝试获取某个锁
	 * 
	 * @param require
	 * @return
	 */
	private Behavior<ClientLockMessage> initLocalClusterLock(
			InitLocalClusterLock require) {
		InterProcessLock lock = 
				this.lockDataCache.getUnchecked(
						require.lockParam);
		require.replyTo.tell(lock);
		return Behaviors.same();
	}

	public static interface ClientLockMessage extends ClusterMessage {
		
		public static class ListingResponse implements ClientLockMessage {
			
			final Receptionist.Listing listing;

			private ListingResponse(
					Receptionist.Listing listing) {
				this.listing = listing;
			}
		}
		
		@AllArgsConstructor
		public static class InitLocalClusterLock implements ClientLockMessage {
			public final ClusterLockAcquireParam lockParam;
			public final ActorRef<InterProcessLock> replyTo;
		}
		
		@NoArgsConstructor
		@AllArgsConstructor
		public static class ThreadReleasedLock implements ClientLockMessage {
			public String lockBusiness;
			
			public ThreadReleasedLock(String lockBusiness) {
				super();
				this.lockBusiness = lockBusiness;
			}

			public long curTime = System.nanoTime();
		}
		
		@NoArgsConstructor
		@AllArgsConstructor
		public static class BusinessAcquireLockRequest implements ClientLockMessage {
			public String lockBusiness;
		}
		
		@AllArgsConstructor
		@NoArgsConstructor
		public static class BusinessLockReleased implements ClientLockMessage {
			public String business;
		}
		
		@NoArgsConstructor
		@AllArgsConstructor
		public static class AcquireResponse implements ClientLockMessage {
			public boolean lockSucc;
			public String business;
			public int version;
		}
		
		@NoArgsConstructor
		@AllArgsConstructor
		public static class AskLockBusinessInfo implements ClientLockMessage {
			public ActorRef<LockCommand> replyTo;
		}
		
		@NoArgsConstructor
		@AllArgsConstructor
		public static class InviteToJoinServer implements ClientLockMessage {
			public ActorRef<LockCommand> serverRef;
		}
		
		@NoArgsConstructor
		@AllArgsConstructor
		public static class LockServerStoped implements ClientLockMessage {
			public ActorRef<LockCommand> serverRef;
		}
		
	}
	

}

