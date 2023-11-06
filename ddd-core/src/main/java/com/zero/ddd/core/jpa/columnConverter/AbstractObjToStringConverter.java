package com.zero.ddd.core.jpa.columnConverter;

import java.util.Optional;

import javax.persistence.AttributeConverter;

import com.zero.ddd.core.classShortName.ClassShortNameCache;

import lombok.extern.slf4j.Slf4j;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time Nov 21, 2020 6:49:49 PM
 * @Desc 些年若许,不负芳华.
 *
 */
@Slf4j
@SuppressWarnings("rawtypes")
public abstract class AbstractObjToStringConverter<O, T> implements AttributeConverter<O, T> {
    
    protected String transTypeToShortName(Object obj) {
        return ClassShortNameCache.findShortName(obj.getClass());
    }
    
    protected Optional<Class> substrTargetTypeToClass(
    		String targetType,
    		boolean isEnum) {
    	if (targetType.contains(".")) {
    		targetType = targetType.substring(targetType.lastIndexOf(".") + 1);
    		return ClassShortNameCache.findClassWithShortNameStatic(
    				targetType, 
    				isEnum);
    	}
    	return Optional.empty();
    }
    
    protected Class transTargetTypeToClass(String targetType, boolean isEnum) {
        return ClassShortNameCache.findClassWithShortNameStatic(
        		targetType, 
        		isEnum)
                .orElseGet(() -> {
                	return this.substrTargetTypeToClass(
                			targetType, 
                			isEnum)
                			.orElseGet(() -> this.forClassName(targetType));
                });
    }

    private Class forClassName(String targetType) {
        try {
        	return AbstractObjToStringConverter.class.getClassLoader().loadClass(targetType);
        } catch (ClassNotFoundException e) {
            log.info("error whie parse targetType:{}", targetType);
            log.error("error:{}", e);
            throw new IllegalArgumentException(targetType + " not exists");
        }
    }

}
