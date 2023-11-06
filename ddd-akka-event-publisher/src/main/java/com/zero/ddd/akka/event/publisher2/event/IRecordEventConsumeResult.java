package com.zero.ddd.akka.event.publisher2.event;


/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2023-08-03 04:49:56
 * @Desc 些年若许,不负芳华.
 *
 */
public interface IRecordEventConsumeResult {
	
	public void recordResult(String synchornizerId, String eventId, String resultMsg);

}