package com.zero.ddd.akka.cluster.job.model.vo;

import java.util.Set;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2023-04-27 01:10:27
 * @Desc 些年若许,不负芳华.
 *
 */
public interface JobTaskAssignor {

	String getServerAssignTo(String taskId);

	Set<String> onlineWorker();

}