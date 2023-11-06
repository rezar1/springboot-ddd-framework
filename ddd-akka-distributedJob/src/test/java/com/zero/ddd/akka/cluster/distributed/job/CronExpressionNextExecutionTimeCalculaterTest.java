package com.zero.ddd.akka.cluster.distributed.job;

import java.time.LocalDateTime;

import org.junit.Test;
import org.springframework.scheduling.support.CronExpression;

import lombok.extern.slf4j.Slf4j;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2023-06-16 02:02:38
 * @Desc 些年若许,不负芳华.
 *
 */
@Slf4j
public class CronExpressionNextExecutionTimeCalculaterTest {
	
	@Test
	public void test() {
		CronExpression calc = CronExpression.parse("0/5 * * * * ?");
		LocalDateTime next = calc.next(LocalDateTime.of(2023, 06, 16, 0, 59, 55));
		log.info("next:{}", next);
	}

}