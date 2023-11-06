package com.zero.ddd.core.helper;

import java.util.Map;

import com.zero.helper.GU;
import com.zero.helper.JacksonUtil;

import lombok.extern.slf4j.Slf4j;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2022-06-27 04:33:29
 * @Desc 些年若许,不负芳华.
 *
 */
@Slf4j
public class JsonStringHelper {
	
	public static <T> T serialToObject(
			String sourceJson, 
			Map<String, Object> appendValues,
			Class<T> clazz) {
		return JacksonUtil.str2ObjNoError(
				appendNewFieldValue(
						sourceJson,
						appendValues),
				clazz);
	}
	
	public static String appendNewFieldValue(
			String sourceJson, 
			Map<String, Object> appendValues) {
		if (GU.isNullOrEmpty(appendValues)) {
			return sourceJson;
		}
		try {
			Map<String, Object> jsonToMap = 
					JacksonUtil.jsonToMap(sourceJson);
			appendValues.entrySet()
			.forEach(entry -> {
				jsonToMap.put(
						entry.getKey(), 
						entry.getValue());
			});
			return JacksonUtil.obj2Str(jsonToMap);
		} catch (Exception e) {
			log.error("appendNewFieldValue error:{}", e);
		}
		return sourceJson;
	}

}

