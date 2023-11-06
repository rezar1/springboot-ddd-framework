package com.zero.ddd.akka.cluster.job.annotations;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import com.zero.ddd.akka.cluster.job.model.vo.JobExecuteType;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2023-04-12 07:22:47
 * @Desc 些年若许,不负芳华.
 *
 */
@Target(METHOD)
@Retention(RUNTIME)
public @interface DistributedJob {
	
	// 作业唯一标识，默认为方法名
	String jobName() default "";
	
	// 作业的中文显示名，用于日志打印和异常通知
	String jobShowName() default "";
	
	// 作业的启动参数
	String jobParams() default "";
	
	// 作业执行模式
	JobExecuteType jobExecuteType() default JobExecuteType.MAP; 
	
	// 最小执行节点数
	int minWorkerCount() default 1;
	
	// 并行度
	int concurrency() default 1;
	
	// 作业使用的调度器
	String runningDispatcher() default "";
	
	// 调度超时设置
	JobScheduleTimeout jobScheduleTimeout() default @JobScheduleTimeout();
	
	// 失败重试策略
	JobRetry jobRetry() default @JobRetry(useing = false);
	
	// 配置的定时调度策略
	JobScheduled jobScheduled() default @JobScheduled(useing = false, cron = "");
	
}