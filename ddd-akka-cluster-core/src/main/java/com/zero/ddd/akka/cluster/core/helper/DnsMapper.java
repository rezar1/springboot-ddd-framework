package com.zero.ddd.akka.cluster.core.helper;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.io.IOUtils;

import com.alibaba.dcm.DnsCacheManipulator;
import com.typesafe.sslconfig.util.ConfigLoader;

import lombok.extern.slf4j.Slf4j;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time Nov 5, 2020 11:15:47 AM
 * @Desc 些年若许,不负芳华.
 *
 */
@Slf4j
public class DnsMapper {

    public static void configDns(
    		String path,
    		boolean fromFilePath) throws IOException {
        if (path == null
        		|| path.trim().isEmpty()) {
            log.warn("dns config file path was empty");
            return;
        }
        InputStream is = null;
        if (fromFilePath) {
            is = new FileInputStream(new File(path));
        } else {
            is = ConfigLoader.class.getClassLoader().getResourceAsStream(path);
        }
        IOUtils.readLines(is, Charset.defaultCharset()).stream().map(str -> {
            String[] split = str.split("=");
            Properties prop = new Properties();
            prop.setProperty(split[0], split[1]);
            log.info("mapper host:{} with ips:{}", split[0], split[1]);
            return prop;
        }).forEach(DnsCacheManipulator::setDnsCache);
    }

	public static void configDns(
			Map<String, String> dnsMapper) {
		if (dnsMapper != null 
        		&& !dnsMapper.isEmpty()) {
            try {
                log.info("try to config dns mapper from:{}", dnsMapper);
                dnsMapper.forEach((host, ip) -> DnsCacheManipulator.setDnsCache(host, ip));
            } catch (Exception e) {
                log.warn("error while parse dns config from file:{}", dnsMapper);
                log.error("ERROR [PARSE DNS FAILURE]:{}", e);
            }
        }
	}

}
