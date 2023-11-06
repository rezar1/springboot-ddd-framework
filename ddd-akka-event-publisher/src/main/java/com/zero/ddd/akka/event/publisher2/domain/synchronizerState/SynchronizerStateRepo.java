package com.zero.ddd.akka.event.publisher2.domain.synchronizerState;

import java.util.concurrent.CompletionStage;

import akka.Done;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2023-06-12 04:05:44
 * @Desc 些年若许,不负芳华.
 *
 */
public interface SynchronizerStateRepo {
	
	public CompletionStage<SynchronizerState> loadSynchronizerState(String eventSynchronizerId);
	
	public CompletionStage<Done> updateSynchronizerState(String eventSynchronizerId, SynchronizerState state);

}