package com.zero.ddd.akka.cluster.toolset;

import java.time.LocalDateTime;

import org.junit.Test;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.zero.helper.JacksonUtil;
import com.zero.helper.LocalDateTimeSerializer;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2023-04-27 08:14:40
 * @Desc 些年若许,不负芳华.
 *
 */
@Slf4j
public class JsonFormatTest {
	
	@Test
	public void testFormat() {
		log.info("output:{}", JacksonUtil.obj2Str(new TestClass()));
	}
	
	@Getter
	private static class TestClass {
		@JsonSerialize(using = LocalDateTimeSerializer.class)
		private LocalDateTime now = LocalDateTime.now();
	}

}

