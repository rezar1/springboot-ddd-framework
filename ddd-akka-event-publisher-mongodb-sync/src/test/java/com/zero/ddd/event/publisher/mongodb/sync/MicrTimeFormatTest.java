package com.zero.ddd.event.publisher.mongodb.sync;

import org.junit.Test;

import com.zero.ddd.akka.event.publisher2.helper.MicrTimeFormat;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2023-07-14 11:55:58
 * @Desc 些年若许,不负芳华.
 *
 */
public class MicrTimeFormatTest {
	
	@Test
	public void test() {
		System.out.println(MicrTimeFormat.currentFormatTime());
		System.out.println(MicrTimeFormat.currentFormatTime());
		System.out.println(MicrTimeFormat.currentFormatTime());
	}

}