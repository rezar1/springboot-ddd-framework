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
	
	String appName() default "";
	String synchronizerId() default "";
	int partition() default 1;
	int clientConcurrency() default 1;
	String runingExecutor() default "";

}