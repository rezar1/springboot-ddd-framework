package com.zero.ddd.akka.cluster.job.model;

import java.util.concurrent.CompletionStage;

import akka.Done;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2023-04-19 02:13:45
 * @Desc 些年若许,不负芳华.
 *
 */
public interface JobDatabase {
	
	public CompletionStage<JobInstanceState> loadJobInstance(String jobCacheName);
	
	public CompletionStage<Done> updateJobInstance(String jobCacheName, JobInstanceState jobInstance);

}