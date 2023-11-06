package com.zero.ddd.akka.cluster.job.annotations;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2023-04-13 03:03:17
 * @Desc 些年若许,不负芳华.
 *
 */
@Retention(RUNTIME)
@Target({ANNOTATION_TYPE})
public @interface JobScheduled {
	
	boolean useing() default true;
	
	/**
	 * A cron-like expression, extending the usual UN*X definition to include triggers
	 * on the second, minute, hour, day of month, month, and day of week.
	 * <p>For example, {@code "0 * * * * MON-FRI"} means once per minute on weekdays
	 * (at the top of the minute - the 0th second).
	 * <p>The fields read from left to right are interpreted as follows.
	 * <ul>
	 * <li>second</li>
	 * <li>minute</li>
	 * <li>hour</li>
	 * <li>day of month</li>
	 * <li>month</li>
	 * <li>day of week</li>
	 * </ul>
	 * <p>The special value {@link #CRON_DISABLED "-"} indicates a disabled cron
	 * trigger, primarily meant for externally specified values resolved by a
	 * <code>${...}</code> placeholder.
	 * @return an expression that can be parsed to a cron schedule
	 * @see org.springframework.scheduling.support.CronSequenceGenerator
	 */
	String cron() default "";
	
	/**
	 * 第一次延迟多长时间后再执行 
	 */
	long initialDelay() default -1;

	/**
	 * 上一次执行完毕时间点之后多长时间再执行
	 * @return
	 */
	long fixedDelay() default -1;

}