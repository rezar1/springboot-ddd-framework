package com.zero.ddd.akka.event.publisher.demo.infa.task;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.springframework.stereotype.Component;

import com.zero.ddd.akka.cluster.job.annotations.DistributedJob;
import com.zero.ddd.akka.cluster.job.annotations.JobScheduleTimeout;
import com.zero.ddd.akka.cluster.job.annotations.JobScheduled;
import com.zero.ddd.akka.cluster.job.definition.JobTaskContext;
import com.zero.ddd.akka.cluster.job.definition.TaskResult;
import com.zero.ddd.akka.cluster.job.definition.listener.JustLogJobScheudleTimeoutListener;
import com.zero.ddd.akka.cluster.job.model.vo.JobExecuteType;
import com.zero.ddd.akka.cluster.job.model.vo.ShardingRequest;
import com.zero.ddd.akka.event.publisher.demo.domain.company.CompanyId;
import com.zero.ddd.akka.event.publisher.demo.domain.users.vo.UserId;
import com.zero.helper.GU;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2023-09-27 03:21:33
 * @Desc 些年若许,不负芳华.
 *
 */
@Component
@Slf4j(topic = "job")
//@Profile({ "test", "prod" })
public class DemoUserDisTask {
	
	private static final String SUB_TASK_1 = "SUB_TASK_1";
	
	/**
	 * 分片执行任务
	 * 
	 * @param context
	 * @return
	 */
	@DistributedJob(
			jobShowName = "分片调度实例",
			concurrency = 10,
			jobParams = "0=run&1=run&2=run&3=run&4=run&5=run&6=run&7=run&8=run&9=run&10=run&11=run",
			jobExecuteType = JobExecuteType.SHARDING,
			jobScheduled = @JobScheduled(cron = "0/5 * * * * ?", initialDelay = 10000),
			jobScheduleTimeout = 
					@JobScheduleTimeout(
							timeout = 3, 
							timeoutUnit = TimeUnit.MINUTES))
	public TaskResult initProfileImageHash(
			JobTaskContext context) {
		ShardingRequest shardingRequest = context.shardingRequest();
		int totalShardingNum = shardingRequest.getShardingNum();
		Long curShardingIndex = shardingRequest.getShardingId();
		String curShardingParam = shardingRequest.getShardingParam();
		log.info(
				"totalShardingNum:{} and curShardingIndex:{}\tcurShardingParam:{}", 
				totalShardingNum, 
				curShardingIndex, 
				curShardingParam);
		// 根据分片总数和当前分片下标取任务数据，一般是mod(taskValue, totalShardingNum) == curShardingIndex;
		return TaskResult.succ();
	}

	/**
	 * 
	 * 拆分大任务至不同实例上执行
	 * 
	 * @param context
	 * @return
	 */
	@DistributedJob(
			// 日志显示可读的任务名称
			jobShowName = "打印所有的公司员工",
			// 任务调度的类型
			jobExecuteType = JobExecuteType.MAP,
			// 单实例并行的任务执行者数量
			concurrency = 5,
			// spring crontab
			jobScheduled = @JobScheduled(cron = "0/20 * * * * ?", initialDelay = 30000),
			// 单次调度超时时间
			jobScheduleTimeout = 
					@JobScheduleTimeout(
							timeout = 10, 
							timeoutUnit = TimeUnit.MINUTES, 
							timeoutListener = JustLogJobScheudleTimeoutListener.class))
	public TaskResult logCompanyUsers(
			JobTaskContext context) {
		if (context.isRootTask()) {
			// 初始根任务
			return this.rootMapSubTask();
		} else if (context.isTaskOfType(SUB_TASK_1)) {
			// 二级实际执行的任务
			return this.executeSubTaskLogic(context);
		} else {} // others
		return TaskResult.succ();
	}

	private TaskResult executeSubTaskLogic(
			JobTaskContext context) {
		BatchSubTaskCommand subTaskCommand = 
				context.task(BatchSubTaskCommand.class);
		subTaskCommand.batchCompanyUserIds
		.forEach(companyAndUser -> {
			// just log it
			log.info("获取到Map任务类型的执行参数:{}", companyAndUser);
		});
		return TaskResult.succ();
	}

	private TaskResult rootMapSubTask() {
		return TaskResult.map(
				SUB_TASK_1,
				GU.split(
						this.loadAllCompanyUser(),
						10)
				.map(BatchSubTaskCommand::new)
				.collect(Collectors.toList()));
	}
	
	private List<CompanyUserSubTask> loadAllCompanyUser() {
		return 
				IntStream.range(0, 20)
				.mapToObj(index -> {
					return 
							new CompanyUserSubTask(
									new CompanyId(index), 
									new UserId(index));
				})
				.collect(Collectors.toList());
	}
	
	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class BatchSubTaskCommand {
		private List<CompanyUserSubTask> batchCompanyUserIds;
	}

	@Data
	@ToString
	@NoArgsConstructor
	@AllArgsConstructor
	public static class CompanyUserSubTask {
		private CompanyId companyId;
		private UserId userId;
	}

}