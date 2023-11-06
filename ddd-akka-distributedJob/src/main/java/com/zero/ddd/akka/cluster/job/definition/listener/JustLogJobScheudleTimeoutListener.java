package com.zero.ddd.akka.cluster.job.definition.listener;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Set;

import com.zero.ddd.akka.cluster.job.definition.JobScheudleTimeoutListener;

import lombok.extern.slf4j.Slf4j;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2023-04-25 02:46:30
 * @Desc 些年若许,不负芳华.
 *
 */
@Slf4j(topic = "job")
public class JustLogJobScheudleTimeoutListener implements JobScheudleTimeoutListener {
	
	protected DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.CHINESE);

	
	public static final JobScheudleTimeoutListener JUST_LOG_TIMEOUT_LISTENER = 
			new JustLogJobScheudleTimeoutListener();

	@Override
	public void onJobScheudleTimeout(
			String jobName,
			LocalDateTime startedAt, 
			Set<String> curOnlilneWorkeres,
			String assignedTaskInfoJson) {
		log.info(
				"任务:{} 开始于:[{}] 执行了:[{} ms], 在线执行节点:[{}] 任务分配情况:[{}] 当前已执行超时，请检查任务性能或者[调整执行Or超时警报]时间", 
				jobName, 
				startedAt.format(formatter),
				Duration.between(
						startedAt,
						LocalDateTime.now())
				.toMillis(),
				curOnlilneWorkeres,
				assignedTaskInfoJson);
	}

}