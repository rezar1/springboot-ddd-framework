package com.zero.ddd.akka.cluster.toolset;

import java.time.LocalDateTime;

import org.junit.Test;
import org.springframework.scheduling.support.CronExpression;

import lombok.extern.slf4j.Slf4j;

/**
 * Unit test for simple App.
 */
@Slf4j
public class AppTest {
	
	@SuppressWarnings("null")
	@Test
	public void testCronExpression() {
		CronExpression expression = 
				CronExpression.parse("0/50 * * * * ?");
		log.info("next:{}", expression.next(LocalDateTime.now()).toString());
		log.info("next:{}", expression.next(LocalDateTime.now()).toString());
	}
	
}