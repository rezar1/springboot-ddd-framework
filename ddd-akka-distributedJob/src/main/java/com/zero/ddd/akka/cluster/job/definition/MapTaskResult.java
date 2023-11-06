package com.zero.ddd.akka.cluster.job.definition;

import java.util.List;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2023-04-24 07:34:40
 * @Desc 些年若许,不负芳华.
 *
 */
@Getter
@RequiredArgsConstructor
public class MapTaskResult<T> {
	private final String taskType;
	private final List<T> tasks;
	
	public static <T> MapTaskResult<T> map(String taskType, List<T> tasks) {
		return new MapTaskResult<>(taskType, tasks);
	}
	
}