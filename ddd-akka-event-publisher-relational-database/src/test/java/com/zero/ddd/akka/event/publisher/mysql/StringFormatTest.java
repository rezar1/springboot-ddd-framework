package com.zero.ddd.akka.event.publisher.mysql;

import org.junit.Test;

import lombok.extern.slf4j.Slf4j;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2023-07-25 06:18:34
 * @Desc 些年若许,不负芳华.
 *
 */
@Slf4j
public class StringFormatTest {
	
	@Test
	public void test() {
		log.info(String.format(
				"select event_id, type_name, event_body, event_time, DATE_FORMAT(insert_time, '%%Y-%%m-%%d %%H:%%i:%%s.%%f') AS insert_time from %s where type_name in(:awareTypes) and insert_time >= :insertTime order by event_id asc limit 1000", 
				"Rezar"));;
	}

}