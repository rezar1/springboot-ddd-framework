package com.zero.ddd.akka.event.publisher2.event;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2023-03-09 05:36:08
 * @Desc 些年若许,不负芳华.
 *
 *       事件处理方法的调用者
 *
 */
public interface EventFunctionInvoker {

	/**
	 * @param eventBody
	 * @throws Exception 
	 */
	public void execute(
			String eventType,
			String eventBody) throws Exception;
	
}