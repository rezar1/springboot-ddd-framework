package com.zero.ddd.akka.cluster.job.model.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2023-04-20 05:30:53
 * @Desc 些年若许,不负芳华.
 *
 */
@Data
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class ShardingRequest {
	
	 /**
     * Sharding id.
     */
    private Long shardingId;

    /**
     * Sharding param.
     */
    private String shardingParam;

    /**
     * Sharding num.
     */
    private Integer shardingNum;

}