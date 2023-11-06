package com.zero.ddd.akka.cluster.job.definition;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import com.zero.ddd.akka.cluster.job.model.JobInstanceState;
import com.zero.ddd.akka.cluster.job.model.vo.JobExecuteType;
import com.zero.ddd.akka.cluster.job.model.vo.JobStateSyncType;
import com.zero.ddd.akka.cluster.job.model.vo.ScheduledTimeType;
import com.zero.helper.GU;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2023-04-21 08:18:24
 * @Desc 些年若许,不负芳华.
 *
 */
@Getter
@Builder
@RequiredArgsConstructor
@EqualsAndHashCode(of = "jobName")
public class JobEndpoint {
	
	private final String jobName;
	private final String jobShowName;
	private final String jobParams;
	private final Integer workerConcurrency;
	private final Integer waitMinWorkerCount;
	private final JobExecuteType jobExecuteType;
	private final JobStateSyncType jobStateSyncType;
	private final JobScheduledConfig jobScheduledConfig;
	private final Optional<JobRetryConfig> jobRetryConfig;
	private final Optional<JobScheduleTimeoutConfig> jobScheduleTimeout;
	private final JobMethodInvoker jobMethodInvoker;
	
	public ScheduledTimeType scheduledTimeType() {
		return this.jobScheduledConfig.getScheduledTimeType();
	} 
	
	public Duration durationWithNextExecutionTime() {
		return this.durationWithNextExecutionTime(
				LocalDateTime.now());
	}
	
	public LocalDateTime nextExtExecutionTime(
			LocalDateTime preStartedAt) {
		LocalDateTime nextExtExecutionTime = 
				jobScheduledConfig.nextExecutionTime(preStartedAt);
		if (preStartedAt == null
				&& this.jobScheduledConfig.getInitialDelay() > 0) {
			return nextExtExecutionTime.plus(
					this.jobScheduledConfig.getInitialDelay(), 
					ChronoUnit.MILLIS);
		}
		return nextExtExecutionTime;
	}
	
	public Duration durationWithNextExecutionTime(
			LocalDateTime preStartedAt) {
		return Duration.between(
				LocalDateTime.now(), 
				this.nextExtExecutionTime(
						preStartedAt));
	}

	public JobInstanceState initJobInstance() {
		return new JobInstanceState();
	}

	public JobStateSyncType jobStateSyncType() {
		return this.jobStateSyncType == null ? 
				JobStateSyncType.ALWAYS : this.jobStateSyncType;
	}

	public LocalDateTime jobScheduleTimeout(
			LocalDateTime localDateTime) {
		return this.jobScheduleTimeout
				.map(timeoutDuration -> {
					return localDateTime
							.plus(
									timeoutDuration.getTimeoutDuration().toMillis(),
									ChronoUnit.MILLIS);
				})
				.orElse(null);
				
	}

	public Optional<JobScheudleTimeoutListener> jobScheduleTimeoutListener() {
		return this.jobScheduleTimeout
				.map(timeout -> timeout.getTimeoutListener());
	}

	public String jobShowName() {
		return GU.isNullOrEmpty(this.jobShowName) ? 
				this.jobName : this.jobShowName;
	}
	
}