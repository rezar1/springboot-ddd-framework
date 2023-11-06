package com.zero.ddd.akka.event.publisher2.event;

import java.util.Optional;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2023-06-12 03:45:48
 * @Desc 些年若许,不负芳华.
 *
 */
public interface IRecordLastOffsetId {
	
	public void saveLastOffset(String key, String lastOffset);
	
	public Optional<String> lastOffset(String key);

}