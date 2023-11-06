package com.zero.ddd.akka.cluster.distributed.job;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.commons.lang3.RandomUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Test;

import com.google.common.collect.Lists;
import com.zero.ddd.akka.cluster.job.model.vo.JobTaskAssignorByConsistentHash;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2023-04-27 11:31:34
 * @Desc 些年若许,不负芳华.
 *
 */
public class JobTaskAssignorTest {
	
	List<String> servers = Lists.newArrayList(
		    "updateTaskJqbGroupChatName-worker-defaultAkkaCluster@10.66.204.195:11000-1951c31d-b716-45eb-b273-5d511ba61edc",
		    "updateTaskJqbGroupChatName-worker-defaultAkkaCluster@10.66.179.164:11000-2e9d16e4-36b7-4bcc-9400-278df5b5b59e",
		    "updateTaskJqbGroupChatName-worker-defaultAkkaCluster@10.66.179.164:11000-4ec6cd33-6385-4cc6-b132-71f66697cbb2",
		    "updateTaskJqbGroupChatName-worker-defaultAkkaCluster@10.66.204.195:11000-3866b560-b22b-460f-8e35-d7685603ec36",
		    "updateTaskJqbGroupChatName-worker-defaultAkkaCluster@10.66.204.195:11000-bda4f4e7-d16b-40e2-b35f-17e5c3db4abd",
		    "updateTaskJqbGroupChatName-worker-defaultAkkaCluster@10.66.179.164:11000-7bb0aead-891e-4c58-95dc-fde39901d9c4",
		    "updateTaskJqbGroupChatName-worker-defaultAkkaCluster@10.66.179.164:11000-33b30aef-9a16-4a1f-8954-44c32b045567",
		    "updateTaskJqbGroupChatName-worker-defaultAkkaCluster@10.66.204.195:11000-070b1b17-7cd2-4602-80dd-f2306525b76b",
		    "updateTaskJqbGroupChatName-worker-defaultAkkaCluster@10.66.204.195:11000-17b1cb02-4bea-4ab5-838b-eda692ab5f53",
		    "updateTaskJqbGroupChatName-worker-defaultAkkaCluster@10.66.179.164:11000-445b8a75-2b5a-43ee-af2a-472059632b6d"
		);
	String REF = "Val";
	
	@Test
	public void test() {
		Map<String, String> onlineWorker = 
				servers.stream()
				.map(server -> {
					return Pair.of(server, REF);
				})
				.collect(
						Collectors.toMap(
								Pair::getKey, 
								pair -> pair.getValue(),
								(v1, v2) -> {
									return v1;
								}));
		JobTaskAssignorByConsistentHash<String> assignor =
				new JobTaskAssignorByConsistentHash<>(
						onlineWorker);
		Map<Integer, String> assignedResult = 
				IntStream.range(0, 100)
				.mapToObj(index -> {
					return Pair.of(
							index,
							assignor.getServerAssignTo("Task-" + index));
				})
				.collect(
						Collectors.toMap(Pair::getLeft, Pair::getRight));
		assignedResult.entrySet()
		.stream()
		.collect(
				Collectors.groupingBy(
						Entry::getValue, 
						Collectors.counting()))
		.forEach((server, count) -> {
			System.out.println("sever:" + server + "\ttaskCount:" + count);
		});
		String remove = 
				servers.remove(
						RandomUtils.nextInt(
								0, 
								servers.size()));
		JobTaskAssignorByConsistentHash<String> assignorTwo = 
				assignor.refreshServers(
						servers.stream()
						.map(server -> {
							return Pair.of(server, REF);
						})
						.collect(
								Collectors.toMap(
										Pair::getKey, 
										pair -> pair.getValue(),
										(v1, v2) -> {
											return v1;
										})));
		
		assignedResult.forEach((taskId, server) -> {
			if (server.contentEquals(remove)) {
				return;
			}
			assert assignorTwo.getServerAssignTo("Task-" + taskId).contentEquals(server);
		});
		
	}

}