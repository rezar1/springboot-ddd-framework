package com.zero.ddd.akka.cluster.toolset.lock.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.google.common.cache.LoadingCache;
import com.zero.ddd.akka.cluster.core.msg.ClusterMessage;
import com.zero.ddd.akka.cluster.toolset.lock.ServiceKeyFactory;
import com.zero.ddd.akka.cluster.toolset.lock.client.ClientLockActor.ClientLockMessage;
import com.zero.ddd.akka.cluster.toolset.lock.client.ClientLockActor.ClientLockMessage.AcquireLockRequest;
import com.zero.ddd.akka.cluster.toolset.lock.client.ClientLockActor.ClientLockMessage.AcquireResponse;
import com.zero.ddd.akka.cluster.toolset.lock.client.ClientLockActor.ClientLockMessage.AskLockBusinessInfo;
import com.zero.ddd.akka.cluster.toolset.lock.client.ClientLockActor.ClientLockMessage.BusinessLockReleased;
import com.zero.ddd.akka.cluster.toolset.lock.client.ClientLockActor.ClientLockMessage.ListingResponse;
import com.zero.ddd.akka.cluster.toolset.lock.client.ClientLockActor.ClientLockMessage.LockAcquireRequest;
import com.zero.ddd.akka.cluster.toolset.lock.client.ClientLockActor.ClientLockMessage.ThreadReleasedLock;
import com.zero.ddd.akka.cluster.toolset.lock.server.ServerLockActor.LockCommand;
import com.zero.ddd.akka.cluster.toolset.lock.server.ServerLockActor.LockCommand.LockAcquireCommand;
import com.zero.ddd.akka.cluster.toolset.lock.server.ServerLockActor.LockCommand.LockClientRegisteCommand;
import com.zero.ddd.akka.cluster.toolset.lock.server.ServerLockActor.LockCommand.LockClientRegisteCommand.LockBusinessInfo;
import com.zero.ddd.akka.cluster.toolset.lock.server.ServerLockActor.LockCommand.ReleaseLockCommand;
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
import akka.actor.typed.receptionist.Receptionist.Listing;
import akka.actor.typed.receptionist.ServiceKey;
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
	
	private static final String NOT_SET = null;
	private ServiceKey<LockCommand> lockClientServiceKey;
	private boolean notFirst;
	private String clientAddress;
	private ActorRef<ClientLockMessage> selfClientRef;
	private StashBuffer<ClientLockMessage> buffer;
	private ActorRef<LockCommand> server;
	
	private LoadingCache<String, InterProcessLock> lockDataCache = 
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
		this.subscribeLockServer(lockCenter);
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
	
	// 监听某个lockCenter的ServerLockActor上线通知
	private void subscribeLockServer(
			String lockCenter) {
		ActorRef<Listing> listingResponseAdapter = 
				getContext().messageAdapter(
						Receptionist.Listing.class, 
						ListingResponse::new);
		this.lockClientServiceKey = 
				ServiceKeyFactory.lockServerServiceKey(lockCenter);
		getContext()
        .getSystem()
        .receptionist()
        .tell(
        		Receptionist.subscribe(
        				this.lockClientServiceKey,
        				listingResponseAdapter));
	}
	
	private InterProcessLock initInterProcessLock(
			String business) {
		// 申请获取某个锁
		this.selfClientRef.tell(
				new LockAcquireRequest(
						business));
		return new InterProcessLock(
				business,
				() -> {
					// 通知远程客户端释放了当前锁
					this.selfClientRef.tell(
							new ThreadReleasedLock(
									business));
				});
	}
	
	@Override
	public Receive<ClientLockMessage> createReceive() {
		return waitRegiste();
	}

	private Receive<ClientLockMessage> waitRegiste() {
		return super.newReceiveBuilder()
				.onMessage(ListingResponse.class, 			this::onLockServerChanaged)
				.onMessage(AskLockBusinessInfo.class,		this::onAskLockBusinessInfo)
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
				.onMessage(ThreadReleasedLock.class, 		this::onThreadReleasedLock)
				.onMessage(LockAcquireRequest.class, 		this::onLockAcquireRequest)
				.onMessage(AcquireResponse.class, 			this::onAcquireResponse)
				.onMessage(ListingResponse.class, 			this::onLockServerChanaged)
				.onMessage(BusinessLockReleased.class,		this::onRemoteBusinessLockReleased)
				.onMessage(AcquireLockRequest.class,		this::onAcquireBusinessLock)
				.onSignal(PostStop.class, 					this::onPostStop)
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
	
	private Behavior<ClientLockMessage> onAskLockBusinessInfo(
			AskLockBusinessInfo msg) {
		this.reportClientLockInfo(
				msg.respId, 
				msg.replyTo);
		return Behaviors.same();
	}
	
	private Behavior<ClientLockMessage> onThreadReleasedLock(
			ThreadReleasedLock msg) {
		this.server.tell(
				new ReleaseLockCommand(
						clientAddress,
						msg.lockBusiness));
		return Behaviors.same();
	}
	
	private Behavior<ClientLockMessage> onLockAcquireRequest(
			LockAcquireRequest msg) {
		this.server.tell(
				new LockAcquireCommand(
						msg.lockBusiness,
						clientAddress));
		return Behaviors.same();
	}
	
	private Behavior<ClientLockMessage> onAcquireResponse(
			AcquireResponse msg) {
		super.getContext().getLog()
		.info(
				"business:{} acquire resp:{} version:{}", 
				msg.business,
				msg.lockSucc,
				msg.version);
		if (msg.lockSucc) {
			InterProcessLock processLock = 
					this.lockDataCache.getUnchecked(
							msg.business);
			processLock.clientServerAcquiredLock();
		}
		return Behaviors.same();
	}
	
	private Behavior<ClientLockMessage> onLockServerChanaged(
			ListingResponse msg) {
		Set<ActorRef<LockCommand>> allServiceInstances = 
				msg.listing.getAllServiceInstances(
						this.lockClientServiceKey);
		if (!allServiceInstances.isEmpty()) {
			allServiceInstances.forEach(server -> {
				if (this.server == null
						|| !this.server.equals(server)) {
					this.server = server;
					// 向服务端注册自身
					this.reportClientLockInfo(NOT_SET, server);
				}
			});
			return this.buffer.unstashAll(this.normalState());
		} else if (this.notFirst) {
			this.server = null;
			return waitRegiste();
		}
		return Behaviors.same();
	}

	private void reportClientLockInfo(
			String respId,
			ActorRef<LockCommand> server) {
		boolean isFirstRegiste = 
				(this.notFirst = (!this.notFirst ? true : false));
		List<LockBusinessInfo> lockBusinessInfoes = new ArrayList<>();
		this.lockDataCache.asMap().entrySet().stream()
		.forEach(entry -> {
			lockBusinessInfoes.add(
					new LockBusinessInfo(
							entry.getKey(),
							entry.getValue().duringLock()));
		});
		server.tell(
				new LockClientRegisteCommand(
						isFirstRegiste,
						respId,
						this.clientAddress,
						lockBusinessInfoes,
						this.selfClientRef));
	}

	/**
	 * 某个business被远程释放了，当前客户端可参与竞争
	 * 
	 * @param released
	 * @return
	 */
	private Behavior<ClientLockMessage> onRemoteBusinessLockReleased(
			BusinessLockReleased released) {
		this.server.tell(
				new LockAcquireCommand(
						released.business,
						this.clientAddress));
		return Behaviors.same();
	}
	
	/**
	 * 客户端尝试获取某个锁
	 * 
	 * @param require
	 * @return
	 */
	private Behavior<ClientLockMessage> onAcquireBusinessLock(
			AcquireLockRequest require) {
		InterProcessLock lock = 
				this.lockDataCache.getUnchecked(
						require.business);
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
		public static class AcquireLockRequest implements ClientLockMessage {
			public final String business;
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
		public static class LockAcquireRequest implements ClientLockMessage {
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
			public String respId;
			public ActorRef<LockCommand> replyTo;
		}
		
	}
	

}

