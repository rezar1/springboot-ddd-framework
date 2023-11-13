package com.zero.ddd.akka.cluster.distributed.job;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;

import org.junit.Test;
import org.springframework.scheduling.support.CronExpression;

import com.zero.ddd.akka.cluster.job.definition.JobScheduledConfig;

import lombok.extern.slf4j.Slf4j;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2023-09-14 12:20:13
 * @Desc 些年若许,不负芳华.
 *
 */
@Slf4j
public class TestCronExpression {

	@Test
	public void testNull() {
		String cronExpressionString = "0 0 0 * * ?";
		CronExpression cronExpression = CronExpression.parse(cronExpressionString);
		// 将当前时间设置为 2023 年 9 月 13 日 23:59:59
		// 计算下一个匹配的时间点
		Instant nextExecutionTime = cronExpression.next(new Date(1234567890000L).toInstant());
		System.out.println("Next execution time: " + nextExecutionTime);
	}

	@Test
	public void test() {
		String data = "2023-11-08 23:59:40.017";
		String format = "yyyy-MM-dd HH:mm:ss.SSS";
		JobScheduledConfig calc = new JobScheduledConfig(3000, "0/40 * * * * ?", null);
		log.info("next:{}", calc.nextExecutionTime(LocalDateTime.parse(data, DateTimeFormatter.ofPattern(format))));
	}

}