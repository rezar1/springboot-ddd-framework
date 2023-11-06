package com.zero.ddd.akka.cluster.core.ip.impl;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

import com.zero.ddd.akka.cluster.core.ip.IAcquisitionOpenIp;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2022-06-29 04:46:49
 * @Desc 些年若许,不负芳华.
 *
 */
@ConditionalOnBean(IAcquisitionOpenIp.class)
@Order(Ordered.LOWEST_PRECEDENCE)
public class DefaultAcquisitionOpenIp implements IAcquisitionOpenIp {

	/**
	 * TODO 从web接口获取当前机器出口ip地址
	 */
	@Override
	public String acquisition() {
		return null;
	}

}

