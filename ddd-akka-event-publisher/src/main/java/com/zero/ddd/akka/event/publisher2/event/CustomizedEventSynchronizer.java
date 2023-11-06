package com.zero.ddd.akka.event.publisher2.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2023-03-09 12:07:13
 * @Desc 些年若许,不负芳华.
 *
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomizedEventSynchronizer {
	
	// 订阅信息
	private EventSynchronizer eventSynchronizer;
	private int concurrency;
	// 事件处理函数
	private EventFunctionInvoker eventFunctionInvoker;
	// 运行线程池(可以特殊设置)
	private String runingExecutor;
	
	
}

