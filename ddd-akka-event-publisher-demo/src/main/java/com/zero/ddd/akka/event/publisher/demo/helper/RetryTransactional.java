package com.zero.ddd.akka.event.publisher.demo.helper;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2023-05-16 07:24:25
 * @Desc 些年若许,不负芳华.
 *
 */
@Inherited
@Documented
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Retryable(
		value = Exception.class, 
		maxAttempts = 50, 
		backoff = @Backoff(delay = 88, random = true, multiplier = 0.21234))
@Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class, timeout = 120)
public @interface RetryTransactional {

}