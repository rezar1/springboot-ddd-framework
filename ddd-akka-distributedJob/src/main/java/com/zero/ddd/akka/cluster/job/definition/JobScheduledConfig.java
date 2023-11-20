package com.zero.ddd.akka.cluster.job.definition;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.scheduling.support.CronExpression;

import com.zero.ddd.akka.cluster.job.model.vo.ScheduledTimeType;
import com.zero.helper.GU;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2023-04-21 08:25:44
 * @Desc 些年若许,不负芳华.
 *
 */
public class JobScheduledConfig {
	
	@Getter
	private final long initialDelay;
	@Getter
	private final String cronExpression;
	@Getter
	private final Duration secondDelay;
	@Getter
	private final ScheduledTimeType scheduledTimeType;
	
	private final NextExecutionTimeCalculater nextExecutionTimeCalculater;
	
	public JobScheduledConfig(
			long initialDelay, 
			String cronExpression, 
			Duration secondDelay) {
		this.initialDelay = initialDelay;
		this.cronExpression = cronExpression;
		this.secondDelay = secondDelay;
		this.scheduledTimeType = 
				this.scheduledTimeType();
		this.nextExecutionTimeCalculater = 
				this.initNextExecutionTimeCalculater();
		
	}
	
	private NextExecutionTimeCalculater initNextExecutionTimeCalculater() {
		if (this.scheduledTimeType == ScheduledTimeType.CRON) {
			return new CronExpressionNextExecutionTimeCalculater(this.cronExpression);
		} else if (this.scheduledTimeType == ScheduledTimeType.SECOND_DELAY) {
			return new SecondDelayNextExecutionTimeCalculater(this.secondDelay);
		}
		throw new IllegalStateException(
				"未支持的ScheduledTimeType类型:" + this.scheduledTimeType);
	}

	public LocalDateTime nextExecutionTime(
			LocalDateTime calculateTime) {
		return 
				this.nextExecutionTimeCalculater.nextExecutionTime(
						calculateTime);
	}
	
	private ScheduledTimeType scheduledTimeType() {
		if (this.secondDelay != null) {
			return ScheduledTimeType.SECOND_DELAY;
		} else if (GU.notNullAndEmpty(this.cronExpression)) {
			return ScheduledTimeType.CRON;
		}
		throw new IllegalStateException(
				"Cron Or secondDelay Both Empty");
	}
	
	@FunctionalInterface
	private static interface NextExecutionTimeCalculater {
		public LocalDateTime nextExecutionTime(LocalDateTime calcTime);
	}
	
	@RequiredArgsConstructor
	private static class SecondDelayNextExecutionTimeCalculater implements NextExecutionTimeCalculater {
		
		private final Duration secondDelay;

		@Override
		public LocalDateTime nextExecutionTime(
				LocalDateTime calcTime) {
			return calcTime.plus(this.secondDelay);
		}
		
	}
	
	private static class CronExpressionNextExecutionTimeCalculater implements NextExecutionTimeCalculater {
		
		private final CronExpression cronCalculater;
		
		public CronExpressionNextExecutionTimeCalculater(
				String cronExpression) {
			this.cronCalculater = 
					this.cronCalculater(cronExpression);
		}
		
		private CronExpression cronCalculater(
				String cronExpression) {
			return Optional.ofNullable(cronExpression)
					.filter(GU::notNullAndEmpty)
					.map(expression -> {
						return CronExpression.parse(expression);
					})
					.orElseThrow(() -> {
						return new IllegalArgumentException(
								"无效的cron表达式:" + cronExpression);
					});
		}

		@Override
		public LocalDateTime nextExecutionTime(
				LocalDateTime calcTime) {
			return 
					Optional.ofNullable(
							cronCalculater
							.next(
									calcTime == null ? 
											LocalDateTime.now() : calcTime))
					.orElse(LocalDateTime.now());
		}
		
	}

}