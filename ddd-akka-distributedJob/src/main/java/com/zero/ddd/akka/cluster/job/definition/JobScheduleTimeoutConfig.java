package com.zero.ddd.akka.cluster.job.definition;

import java.time.Duration;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2023-04-20 07:44:36
 * @Desc 些年若许,不负芳华.
 *
 */
@Getter
@AllArgsConstructor
public class JobScheduleTimeoutConfig {
	private final Duration timeoutDuration;
	private final JobScheudleTimeoutListener timeoutListener;
}