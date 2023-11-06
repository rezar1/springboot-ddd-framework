package com.zero.ddd.akka.event.publisher2.event.annotations;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2023-06-29 01:04:00
 * @Desc 些年若许,不负芳华.
 *
 */
@Retention(RUNTIME)
@Target({METHOD, PARAMETER})
public @interface ShardingKeyExpression {
	
	String el();
	String filterEl() default "";

}