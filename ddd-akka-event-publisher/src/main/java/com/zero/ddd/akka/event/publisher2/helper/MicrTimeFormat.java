package com.zero.ddd.akka.event.publisher2.helper;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2023-07-14 11:25:44
 * @Desc 些年若许,不负芳华.
 *
 */
public class MicrTimeFormat {
	
	private static final DateTimeFormatter format = 
			DateTimeFormatter.ofPattern(
					"yyyy-MM-dd HH:mm:ss.SSS");
	
	public static String currentFormatTime() {
		long nanoTime = System.nanoTime(); 
		long microTime = nanoTime / 1000; 
		String microSeconds = String.format("%06d", microTime % 1000000); 
		return LocalDateTime.now().format(format) + microSeconds;
	}
	
}