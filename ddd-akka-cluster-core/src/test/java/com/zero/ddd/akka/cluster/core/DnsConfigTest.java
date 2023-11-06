package com.zero.ddd.akka.cluster.core;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.zero.ddd.akka.cluster.core.initializer.config.DnsConfig;
import com.zero.helper.GU;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2022-07-15 12:31:11
 * @Desc 些年若许,不负芳华.
 *
 */
public class DnsConfigTest {
	
	@Test
	public void testDnsConfig() {
		DnsConfig config = new DnsConfig();
		config.setUrl("https://storage.zmeng123.com/weike/signed.txt");
		assertTrue(GU.notNullAndEmpty(config.getDnsMapper()));
		assertTrue(config.getShowHostName("127.0.0.1").contentEquals("weike01"));
		assertTrue(config.getShowHostName("127.0.0.3").contentEquals("weike03"));
	}

}

