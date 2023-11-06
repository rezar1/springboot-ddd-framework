package com.zero.ddd.akka.cluster.core.initializer.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.tuple.Pair;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.zero.ddd.akka.cluster.core.helper.HttpUtil;
import com.zero.helper.GU;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2022-04-08 11:18:40
 * @Desc 些年若许,不负芳华.
 *
 */
@Data
@Slf4j
public class DnsConfig {
	
	private String url;
	private boolean autoRefresh;
	private BiMap<String, String> dnsMapper = HashBiMap.create();
	
	public void setUrl(
			String dnsUrl) {
		this.url = dnsUrl;
		this.parseUrlToMapper()
		.forEach(pair -> {
			String host = pair.getKey();
			String ip = pair.getValue();
			if (!this.dnsMapper.containsKey(host)
					|| !dnsMapper.get(host).contentEquals(ip)) {
				this.dnsMapper.forcePut(host, ip);
			}
		});
	}

	public Map<String, String> getDnsMapper() {
		return Collections.unmodifiableMap(this.dnsMapper);
	}
	
	public String getShowHostName(String ip) {
		return this.dnsMapper.inverse().get(ip);
	}
	
	public void setDnsMapper(
			Map<String, String> dnsMapper) {
		if (GU.notNullAndEmpty(dnsMapper)) {
			this.dnsMapper.putAll(dnsMapper);
		}
	}
	
	public Map<String, String> refresh() {
		if (log.isDebugEnabled()) {
			log.debug("try refresh dns from url:{}", this.url);
		}
		this.setUrl(url);
		return this.getDnsMapper();
	}
	
	private List<Pair<String, String>> parseUrlToMapper() {
		return 
				HttpUtil.readFromUrl(url)
				.map(datas -> {
					String regex = "(.+?)=(([0,1]?\\d{1,2}|2([0-4][0-9]|5[0-5]))(\\.([0,1]?\\d{1,2}|2([0-4][0-9]|5[0-5]))){3})";
					String configProp = new String(datas);
					if (log.isDebugEnabled()) {
						log.debug("load dns config:{}", configProp);
					}
					Matcher matcher = 
							Pattern.compile(regex)
							.matcher(configProp);
					List<Pair<String, String>> mapper = new ArrayList<>();
					while (matcher.find()) {
						String host = matcher.group(1);
						String ip = matcher.group(2);
						mapper.add(Pair.of(
									host, 
									ip));
					}
					return mapper;
				})
				.orElse(GU.emptyList());
	}
	
}

