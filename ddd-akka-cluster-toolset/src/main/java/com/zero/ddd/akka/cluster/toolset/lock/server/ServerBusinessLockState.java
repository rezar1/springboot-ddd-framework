package com.zero.ddd.akka.cluster.toolset.lock.server;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.zero.ddd.akka.cluster.core.msg.ClusterMessage;
import com.zero.helper.GU;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2022-07-15 04:17:46
 * @Desc 些年若许,不负芳华.
 *
 */
@NoArgsConstructor
@Data
@ToString
public class ServerBusinessLockState implements ClusterMessage {
	
	private Map<String, BusinessLockData> lockMap = new HashMap<>();
	
	public void attemptLockBusiness(
			String lockBusiness,
			String client) {
		BusinessLockData businessLockData = 
				this.getOrInitBusinessLockData(
						lockBusiness);
		businessLockData.joinNewParticipantClient(client);
	}
	
	public void rebuildLockBusinessInfo(
			String lockBusiness,
			String client,
			boolean ownLock) {
		this.getOrInitBusinessLockData(lockBusiness)
		.rebuildParticipantClient(client, ownLock);
	}
	
	public int lockVersion(String lockBusiness) {
		return this.lockDataOpt(lockBusiness)
				.map(data -> data.version)
				.orElse(0);
	}
	
	public boolean hasLockedByClient(
			String lockBusiness, 
			String lockParticipantClient) {
		return this.lockDataOpt(
				lockBusiness)
				.map(data -> data.whetherCanLockByClient(lockParticipantClient))
				.orElse(false);
	}
	
	public CurLockedBusinessInfo curLockBusinessClient(
			String lockBusiness) {
		return this.lockDataOpt(lockBusiness)
				.map(data -> {
					String curLockBusinessClient = 
							data.curLockBusinessClient();
					if (curLockBusinessClient != null) {
						return new CurLockedBusinessInfo(
								curLockBusinessClient,
								data.version);
					}
					return null;
				})
				.orElse(null);
	}
	
	/**
	 * 获取被某个客户端锁定的所有锁资源
	 * 
	 * @param lockParticipantClient
	 * @return
	 */
	public List<String> lockedByClient(
			String lockParticipantClient) {
		return this.lockMap.entrySet()
				.stream()
				.filter(entry -> {
					return entry.getValue()
							.haslockParticipantClient(
									lockParticipantClient)
							&& entry.getValue().whetherCanLockByClient(
									lockParticipantClient);
				})
				.map(entry -> entry.getKey())
				.collect(Collectors.toList());
	}
	
	public Set<String> allLockParticipantClientAddress() {
		return this.lockMap.entrySet()
				.stream()
				.flatMap(entry -> entry.getValue().waitAcquireClientAddress().stream())
				.collect(Collectors.toSet());
	}

	public List<String> listWaitAcquireLockClient(
			String lockedBusiness) {
		return this.getOrInitBusinessLockData(lockedBusiness)
				.waitAcquireClientAddress();
	}

	public ServerBusinessLockState releaseClient(
			String lockParticipantClient) {
		this.lockMap.entrySet()
		.stream()
		.filter(entry -> {
			return entry.getValue()
					.haslockParticipantClient(
							lockParticipantClient);
		})
		.forEach(entry -> {
			entry.getValue()
			.releaseClient(
					lockParticipantClient);
		});
		return this;
	}
	
	public ServerBusinessLockState releaseClientes(
			Set<String> clientes) {
		if (GU.notNullAndEmpty(clientes)) {
			clientes
			.forEach(this::releaseClient);
		}
		return this;
	}

	public ServerBusinessLockState releaseBusinessLock(
			String business, 
			String client) {
		this.lockDataOpt(business)
		.ifPresent(lockData -> {
			lockData.releaseClient(client);
		});
		return this;
	}
	
	private BusinessLockData getOrInitBusinessLockData(
			String lockBusiness) {
		BusinessLockData lockData = 
				this.lockMap.get(lockBusiness);
		if (lockData == null) {
			lockData = new BusinessLockData();
			this.lockMap.put(lockBusiness, lockData);
		}
		return lockData;
	}
	
	private Optional<BusinessLockData> lockDataOpt(
			String lockBusiness){
		return Optional.ofNullable(
				this.lockMap.get(lockBusiness));
	}

	static class BusinessLockData {
		
		int version;
		
		private List<String> allAcquireClient = new LinkedList<>();
		
		boolean duringLock;
		
		public void duringLock() {
			this.duringLock = true;
		}
		
		public void notDuringLock() {
			this.duringLock = false;
		}
		
		public void rebuildParticipantClient(
				String clientAddress,
				boolean ownLock) {
			if (!ownLock) {
				this.allAcquireClient.add(clientAddress);
			} else {
				this.duringLock();
				this.allAcquireClient.add(0, clientAddress);
			}
		}
		
		public void joinNewParticipantClient(
				String clientAddress) {
			if (!this.allAcquireClient.contains(
					clientAddress)) {
				this.allAcquireClient.add(clientAddress);
			}
		}
		
		public String curLockBusinessClient() {
			return allAcquireClient.isEmpty() ? 
					null : allAcquireClient.get(0);
		}

		public List<String> waitAcquireClientAddress() {
			return this.allAcquireClient.stream()
					.collect(Collectors.toList());
		}

		public boolean haslockParticipantClient(
				String lockParticipantClient) {
			return allAcquireClient.contains(lockParticipantClient);
		}

		public void releaseClient(
				String client) {
			this.version += 1;
			this.allAcquireClient.remove(client);
			this.allAcquireClient.add(client);
		}
		
		public boolean whetherCanLockByClient(
				String client) {
			if (allAcquireClient.isEmpty()) {
				return false;
			}
			return curLockBusinessClient().contentEquals(client);
		}
		
	}
	
	@NoArgsConstructor
	@AllArgsConstructor
	public static class CurLockedBusinessInfo {
		public String lockedBy;
		public int lockVersion;
	}

}

