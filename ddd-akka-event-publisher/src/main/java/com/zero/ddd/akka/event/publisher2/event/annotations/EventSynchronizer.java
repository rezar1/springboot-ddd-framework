package com.zero.ddd.akka.event.publisher2.event.annotations;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2023-03-12 10:21:08
 * @Desc 些年若许,不负芳华.
 *
 */
@Inherited
@Target({METHOD})
@Retention(RUNTIME)
public @interface EventSynchronizer {
	
	/**
	 * Event发布方对应的服务名
	 * 
	 * @return
	 */
	String appName() default "";
	/**
	 * 事件消费业务的标识ID(单一appName下需保证唯一)
	 * @return
	 */
	String synchronizerId() default "";
	/**
	 * 事件总分区数
	 * @return
	 */
	int partition() default 1;
	/**
	 * 客户端事件消费并行度, 类似forkjoin-pool里的worker(不是单独的线程)
	 * @return
	 */
	int clientConcurrency() default 1;
	/**
	 * 批量获取事件消费
	 * 
	 * @return
	 */
	BatchConsume batchConsume() default @BatchConsume(useing = false);
	/**
	 * 任务调度的线程池，暂未支持
	 * @return
	 */
	@Deprecated
	String runingExecutor() default "";

}