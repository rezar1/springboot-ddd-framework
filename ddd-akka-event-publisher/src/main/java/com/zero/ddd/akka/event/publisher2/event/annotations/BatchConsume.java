package com.zero.ddd.akka.event.publisher2.event.annotations;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2023-12-07 02:37:48
 * @Desc 些年若许,不负芳华.
 *
 */
@Retention(RUNTIME)
@Target(ANNOTATION_TYPE)
public @interface BatchConsume {
	
	boolean useing() default true;
	int batchSize() default 50;
	long timeWindows() default 50l;
	TimeUnit timeWindowsUnit() default TimeUnit.MILLISECONDS;

}