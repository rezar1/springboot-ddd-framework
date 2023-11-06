package com.zero.ddd.akka.event.publisher2.event.annotations;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2023-03-13 02:07:18
 * @Desc 些年若许,不负芳华.
 *
 */
@Inherited
@Target(TYPE)
@Retention(RUNTIME)
public @interface EventAppServerName {
	
	String value();

}

