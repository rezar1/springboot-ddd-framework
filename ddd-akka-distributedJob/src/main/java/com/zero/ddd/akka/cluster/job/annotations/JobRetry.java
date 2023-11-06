package com.zero.ddd.akka.cluster.job.annotations;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2023-03-12 10:42:26
 * @Desc 些年若许,不负芳华.
 *
 */
@Retention(RUNTIME)
@Target({ANNOTATION_TYPE})
public @interface JobRetry {
	
	boolean useing() default true;
	
	/**
	 * 最少等待时长
	 * 
	 * @return
	 */
	long minBackoffMill() default 10;
	
	/**
	 * 最大等待时长
	 * 
	 * @return
	 */
	long maxBackoffMill() default 5000;
	
	/**
	 * 随机因子
	 * 
	 * @return
	 */
	double randomFactor() default 0.2d;
	
	/**
	 * 最大重试次数
	 * 
	 * @return
	 */
	int maxRetries() default 5;
	
}

