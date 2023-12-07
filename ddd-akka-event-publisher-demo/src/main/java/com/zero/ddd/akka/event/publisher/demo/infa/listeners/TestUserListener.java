package com.zero.ddd.akka.event.publisher.demo.infa.listeners;

import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Component;

import com.zero.ddd.akka.event.publisher.demo.domain.users.dmEvents.DemoUserProfileChanged;
import com.zero.ddd.akka.event.publisher.demo.domain.users.dmEvents.NewDemoUser;
import com.zero.ddd.akka.event.publisher2.event.annotations.BatchConsume;
import com.zero.ddd.akka.event.publisher2.event.annotations.EventAppServerName;
import com.zero.ddd.akka.event.publisher2.event.annotations.EventSynchronizer;
import com.zero.ddd.akka.event.publisher2.event.annotations.ShardingKeyExpression;

import lombok.extern.slf4j.Slf4j;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2023-09-26 08:33:27
 * @Desc 些年若许,不负芳华.
 *
 */
@Slf4j
@Component
@EventAppServerName("event-publisher-demo") // 制定消费的微服务「spring.application.name」的名称, 集群中会以这个名称区分不同的服务
public class TestUserListener {
	
	// 多机部署下，partition为【onDemoUserEventAndJustLog】关注的事件按照规则分区的数量
	// clientConcurrency为单个服务实例启动消费【updateParticipantsBelongChain】事件的消费者的数量，尽量(clientConcurrency * nodeCount) >= partition以避免多分区分配给单消费者上
	// 消费者不是一个线程的概念，有点类似于forkjoin-pool的worker角色(单独的事件队列，没有工作窃取，共用调度线程池)
	@EventSynchronizer(
			partition = 1, 
			clientConcurrency = 1,
			batchConsume = 
				@BatchConsume(
						useing = true,
						batchSize = 1000,
						timeWindows = 1000, 
						timeWindowsUnit = TimeUnit.MILLISECONDS))
	// @ShardingKeyExpression放在方法层级，表示所有的事件都按照相同的策略进行取值计算分区值，底层使用拼凑后的字符串的hash值进行计算分区值
	// 允许参数级别单独制定事件自身的分区取值策略
	// filterEl为事件过滤策略，表示只消费满足条件的事件
	@ShardingKeyExpression(
			// 以事件的[companyId + userId]为分区策略，保证同一个公司下同一个员工ID的事件由单一消费者消费
			el ="#event[companyId] + '-' + #event[userId]")
	public void onDemoUserEventAndJustLog(
			NewDemoUser newDemoUser,
			@ShardingKeyExpression(
					el ="#event[companyId] + '-' + #event[userId]",
					filterEl = "#event[userProfile] != null")
			DemoUserProfileChanged demoUserProfileChanged) {
		if (newDemoUser != null) {
//			log.info("newDemoUser:{}", newDemoUser);
			// 其他业务逻辑调用
		} else if (demoUserProfileChanged != null) {
			log.info("demoUserProfileChanged:{}", demoUserProfileChanged);
			// 其他业务逻辑调用
		}
	}

}