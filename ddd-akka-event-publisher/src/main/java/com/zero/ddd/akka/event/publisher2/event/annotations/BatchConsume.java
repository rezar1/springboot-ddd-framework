package com.zero.ddd.akka.event.publisher2.event.annotations;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2023-12-07 02:37:48
 * @Desc 些年若许,不负芳华.
 * 
 * 允许在：
 * 获取到{batchSize}或者时间窗口达到{timeWindowMill}长度后，将这一批次里的事件聚合成一组下发到客户端处理
 * 
 * 合规参数范围: batchSize在[1, 2000} 且 timeWindowMill在[1, 15000}
 * 
 */
@Retention(RUNTIME)
@Target(ANNOTATION_TYPE)
public @interface BatchConsume {
	
	boolean useing() default true;
	int batchSize() default 50;
	long timeWindowMill() default 50l;

}