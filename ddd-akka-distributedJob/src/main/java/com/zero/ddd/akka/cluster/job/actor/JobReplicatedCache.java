package com.zero.ddd.akka.cluster.job.actor;


import java.time.Duration;
import java.util.Optional;

import com.zero.ddd.akka.cluster.core.initializer.serializer.SelfProtoBufObject;
import com.zero.ddd.akka.cluster.job.model.JobInstanceState;

import akka.Done;
import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.cluster.ddata.Key;
import akka.cluster.ddata.LWWMap;
import akka.cluster.ddata.LWWMapKey;
import akka.cluster.ddata.LWWRegister.Clock;
import akka.cluster.ddata.SelfUniqueAddress;
import akka.cluster.ddata.typed.javadsl.DistributedData;
import akka.cluster.ddata.typed.javadsl.Replicator.Get;
import akka.cluster.ddata.typed.javadsl.Replicator.GetResponse;
import akka.cluster.ddata.typed.javadsl.Replicator.GetSuccess;
import akka.cluster.ddata.typed.javadsl.Replicator.NotFound;
import akka.cluster.ddata.typed.javadsl.Replicator.ReadConsistency;
import akka.cluster.ddata.typed.javadsl.Replicator.ReadMajority;
import akka.cluster.ddata.typed.javadsl.Replicator.Update;
import akka.cluster.ddata.typed.javadsl.Replicator.UpdateResponse;
import akka.cluster.ddata.typed.javadsl.Replicator.WriteConsistency;
import akka.cluster.ddata.typed.javadsl.Replicator.WriteMajority;
import akka.cluster.ddata.typed.javadsl.ReplicatorMessageAdapter;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import scala.Option;

public class JobReplicatedCache {
	
	private static final WriteConsistency writeMajority = new WriteMajority(Duration.ofSeconds(20));
	private static final ReadConsistency readMajority = new ReadMajority(Duration.ofSeconds(20));
	

	public interface JobCacheCommand extends SelfProtoBufObject {
	}

	public static class PutInCache implements JobCacheCommand {
		public final String key;
		public final JobInstanceState value;
		public final ActorRef<Done> replyTo;

		public PutInCache(
				String key, 
				JobInstanceState value,
				ActorRef<Done> replyTo) {
			this.key = key;
			this.value = value;
			this.replyTo = replyTo;
		}
	}

	public static class GetFromCache implements JobCacheCommand {
		public final String key;
		public final ActorRef<Cached> replyTo;

		public GetFromCache(String key, ActorRef<Cached> replyTo) {
			this.key = key;
			this.replyTo = replyTo;
		}
	}

	@Getter
	public static class Cached {
		public final String key;
		public final Optional<JobInstanceState> value;

		public Cached(String key, Optional<JobInstanceState> value) {
			this.key = key;
			this.value = value;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((key == null) ? 0 : key.hashCode());
			result = prime * result + ((value == null) ? 0 : value.hashCode());
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			Cached other = (Cached) obj;
			if (key == null) {
				if (other.key != null)
					return false;
			} else if (!key.equals(other.key))
				return false;
			if (value == null) {
				if (other.value != null)
					return false;
			} else if (!value.equals(other.value))
				return false;
			return true;
		}

		@Override
		public String toString() {
			return "Cached [key=" + key + ", value=" + value + "]";
		}

	}

	@RequiredArgsConstructor
	public static class Evict implements JobCacheCommand {
		public final String key;
		public final ActorRef<Done> replyTo;

	}

	private interface InternalCommand extends JobCacheCommand {
	}

	private static class InternalGetResponse implements InternalCommand {
		public final String key;
		public final ActorRef<Cached> replyTo;
		public final GetResponse<LWWMap<String, JobInstanceState>> rsp;

		private InternalGetResponse(
				String key, 
				ActorRef<Cached> replyTo, 
				GetResponse<LWWMap<String, JobInstanceState>> rsp) {
			this.key = key;
			this.rsp = rsp;
			this.replyTo = replyTo;
		}
	}

	@RequiredArgsConstructor
	private static class InternalUpdateResponse implements InternalCommand {
		
		@SuppressWarnings("unused")
		public final UpdateResponse<LWWMap<String, JobInstanceState>> rsp;
		public final ActorRef<Done> replyTo;

	}

	public static Behavior<JobCacheCommand> create() {
		return Behaviors.setup(
				context -> {
					return DistributedData.withReplicatorMessageAdapter(
							(ReplicatorMessageAdapter<JobCacheCommand, LWWMap<String, JobInstanceState>> replicator) -> 
								new JobReplicatedCache(
										context, 
										replicator)
								.createBehavior());
				});
	}

	private final SelfUniqueAddress node;
	private final ReplicatorMessageAdapter<JobCacheCommand, LWWMap<String, JobInstanceState>> replicator;

	public JobReplicatedCache(
			ActorContext<JobCacheCommand> context,
			ReplicatorMessageAdapter<JobCacheCommand, LWWMap<String, JobInstanceState>> replicator) {
		this.replicator = replicator;
		this.node = 
				DistributedData.get(
						context.getSystem()).selfUniqueAddress();
	}

	public Behavior<JobCacheCommand> createBehavior() {
		return Behaviors.receive(JobCacheCommand.class)
				.onMessage(PutInCache.class, cmd -> receivePutInCache(cmd.key, cmd.value, cmd.replyTo))
				.onMessage(Evict.class, cmd -> receiveEvict(cmd.key, cmd.replyTo))
				.onMessage(GetFromCache.class, cmd -> receiveGetFromCache(cmd.key, cmd.replyTo))
				.onMessage(InternalGetResponse.class, this::onInternalGetResponse)
				.onMessage(
						InternalUpdateResponse.class, 
						update -> {
							update.replyTo.tell(Done.done());
							return Behaviors.same();
						})
				.build();
	}
	
	private static Clock<JobInstanceState> clock = (time, val) -> val.getVersion();

	private Behavior<JobCacheCommand> receivePutInCache(
			String key, 
			JobInstanceState value, 
			ActorRef<Done> replyTo) {
		replicator.askUpdate(
				askReplyTo -> 
					new Update<>(
						dataKey(key), 
						LWWMap.empty(), 
						writeMajority, 
						askReplyTo, 
						curr -> curr.put(node, key, value, clock)), 
				data -> {
					return new InternalUpdateResponse(data, replyTo);
				});
		return Behaviors.same();
	}

	private Behavior<JobCacheCommand> receiveEvict(
			String key, 
			ActorRef<Done> replyTo) {
		replicator.askUpdate(
				askReplyTo -> 
					new Update<>(
						dataKey(key),
						LWWMap.empty(),
						writeMajority,
						askReplyTo, 
						curr -> curr.remove(node, key)), 
				data -> {
					return new InternalUpdateResponse(data, replyTo);
				});
		return Behaviors.same();
	}

	private Behavior<JobCacheCommand> receiveGetFromCache(
			String key, 
			ActorRef<Cached> replyTo) {
		replicator.askGet(
				askReplyTo -> 
					new Get<>(
						dataKey(key), 
						readMajority, 
						askReplyTo), 
				rsp -> new InternalGetResponse(
						key, 
						replyTo, 
						rsp));
		return Behaviors.same();
	}

	private Behavior<JobCacheCommand> onInternalGetResponse(
			InternalGetResponse msg) {
		if (msg.rsp instanceof GetSuccess) {
			Option<JobInstanceState> valueOption = 
					((GetSuccess<LWWMap<String, JobInstanceState>>) msg.rsp)
					.get(
							dataKey(msg.key))
					.get(msg.key);
			Optional<JobInstanceState> valueOptional = 
					Optional.ofNullable(
							valueOption.isDefined() ? valueOption.get() : null);
			msg.replyTo.tell(
					new Cached(
							msg.key, 
							valueOptional));
		} else if (msg.rsp instanceof NotFound) {
			msg.replyTo.tell(
					new Cached(
							msg.key, 
							Optional.empty()));
		}
		return Behaviors.same();
	}

	private Key<LWWMap<String, JobInstanceState>> dataKey(String entryKey) {
		return LWWMapKey.create("job-cache-" + Math.abs(entryKey.hashCode() % 100));
	}

}