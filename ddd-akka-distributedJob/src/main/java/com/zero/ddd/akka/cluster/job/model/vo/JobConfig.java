package com.zero.ddd.akka.cluster.job.model.vo;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2023-04-19 12:50:42
 * @Desc 些年若许,不负芳华.
 *
 */
@Getter
@AllArgsConstructor
public class JobConfig {
	
	private String jobName;
	private int minWorkerCount;

}