package com.zero.ddd.akka.cluster.distributed.job;

import org.junit.Test;

import com.zero.ddd.akka.cluster.core.helper.ProtoBufSerializeUtils;
import com.zero.ddd.akka.cluster.job.actor.JobScheduler.JobScheduledCommand;
import com.zero.ddd.akka.cluster.job.actor.JobScheduler.ScheduleJobOperation;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2023-04-25 12:16:20
 * @Desc 些年若许,不负芳华.
 *
 */
public class EnumProtobufTest {
	
	@Test
	public void test() {
		Command command1 = new Command(ScheduleJobOperation.START_JOB_SCHEDULER);
		assert command1.command.equals(ScheduleJobOperation.START_JOB_SCHEDULER);
		Command command2 = 
				ProtoBufSerializeUtils.deserialize(
						ProtoBufSerializeUtils.serialize(command1),
						Command.class);
		assert command2.command.equals(ScheduleJobOperation.START_JOB_SCHEDULER);
	}
	
	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	private static class Command {
		private JobScheduledCommand command;
	}
	
}