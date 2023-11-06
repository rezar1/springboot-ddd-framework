package com.zero.ddd.akka.cluster.core.initializer.config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.zero.helper.GU;

import lombok.Data;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2022-07-08 10:33:06
 * @Desc 些年若许,不负芳华.
 *
 */
@Data
public class AkkaOverrideConfig {
	
	private Map<String, Object> overrideMap = new HashMap<>();
	private List<Config> resolveWithConfig = new ArrayList<>();
	
	public void appendOverride(String key, Object value) {
		this.overrideMap.put(key, value);
	}
	
	@SuppressWarnings("unchecked")
	public <T> T getValue(String key) {
		if (!this.overrideMap.containsKey(key)) {
			return null;
		}
		return (T) this.overrideMap.get(key);
	}
	
	public void resolveWith(
			String configResource) {
		Config load = 
				ConfigFactory.load(configResource);
		if (load != null) {
			this.resolveWithConfig.add(load);
		}
	}
	
	public Config parseAllConfig(String fallback) {
		Config config = 
        		ConfigFactory.parseMap(
        				overrideMap);
		if (GU.notNullAndEmpty(
				this.resolveWithConfig)) {
			for (Config configItem : this.resolveWithConfig) {
				config = config.resolveWith(configItem);
			}
		}
		return config.withFallback(
        				ConfigFactory.load(
        						fallback));
	}

}

