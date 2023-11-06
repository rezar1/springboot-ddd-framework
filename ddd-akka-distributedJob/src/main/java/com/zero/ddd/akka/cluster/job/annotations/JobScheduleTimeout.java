package com.zero.ddd.akka.cluster.job.annotations;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

import com.zero.ddd.akka.cluster.job.definition.JobScheudleTimeoutListener;
import com.zero.ddd.akka.cluster.job.definition.listener.JustLogJobScheudleTimeoutListener;

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
public @interface JobScheduleTimeout {

	long timeout() default -1l;
	TimeUnit timeoutUnit() default TimeUnit.MILLISECONDS;
	Class<? extends JobScheudleTimeoutListener> timeoutListener() default JustLogJobScheudleTimeoutListener.class;
	
}