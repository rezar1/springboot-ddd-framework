package com.zero.ddd.core.classShortName;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.zero.helper.GU;

import lombok.extern.slf4j.Slf4j;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time Nov 21, 2020 5:28:35 PM
 * @Desc 些年若许,不负芳华.
 *
 */
@Slf4j
@SuppressWarnings("rawtypes")
public class ClassShortNameCache {
    
	private volatile static ClassShortNameCache SHORT_MAPPER = new ClassShortNameCache();
    
    private volatile boolean initDefault = true;
    
    private BiMap<String, Class> normalClassMap = HashBiMap.create();
    private BiMap<String, Class> enumClassMap = HashBiMap.create();
    
    synchronized static void initStatic(
			Set<Class<?>> scanPackagesAndRetTargetClass) {
    	SHORT_MAPPER = 
    			new ClassShortNameCache(scanPackagesAndRetTargetClass);
	}
    
    private ClassShortNameCache() {
    	this(GU.emptySet());
    }
    
    private ClassShortNameCache(
    		Set<Class<?>> needShortClasses) {
    	this.initFromAllNeedShortClasses(
    			needShortClasses);
    }
    
    private void initFromAllNeedShortClasses(
    		Set<Class<?>> needShortClasses) {
    	needShortClasses.stream()
		.collect(Collectors.groupingBy(Class::isEnum))
		.forEach((isEnum,value) -> {
			value.stream().forEach(clazz -> {
				BiMap<String, Class> tmp = isEnum ? enumClassMap : normalClassMap;
				putWithCheckExists(tmp, clazz);
			});
		});
    	this.initDefault = false;
        SHORT_MAPPER = this;
	}

    private void putWithCheckExists(BiMap<String, Class> tmp, Class<?> clazz) {
        String shortName = clazz.getSimpleName();
        NeedCacheShortName annotation = clazz.getAnnotation(
                NeedCacheShortName.class);
        if (annotation == null) {
        	return;
        }
        if (GU.notNullAndEmpty(annotation.shortName())) {
            shortName = annotation.shortName();
        }
        if (tmp.containsKey(shortName)) {
            Class class1 = this.normalClassMap.get(shortName);
            throw new IllegalStateException(
                    "shortName: " + shortName + " has aleady Related with class:" + class1);
        }
        log.info("FOUND\t[" + shortName + "\t:\t" +clazz + "]");
        tmp.put(shortName, clazz);
    }

    public Class findNormalClassWithShortName(String shrotName) {
        return this.normalClassMap.get(shrotName);
    }
    
    public Class findEnumClassWithShortName(String shrotName) {
        return this.enumClassMap.get(shrotName);
    }
    
    public static Class findNormalClassWithShortNameStatic(String shrotName) {
        return SHORT_MAPPER.findNormalClassWithShortName(shrotName);
    }
    
    private Optional<Class> findNormalClassWithShortNameNoError(String shrotName) {
        if (this.initDefault) {
            return Optional.empty();
        }
        return Optional.ofNullable(this.normalClassMap.get(shrotName));
    }
    private Optional<Class> findEnumClassWithShortNameNoError(String shrotName) {
        if (this.initDefault) {
            return Optional.empty();
        }
        return Optional.ofNullable(this.enumClassMap.get(shrotName));
    }
    
    public static Class findEnumClassWithShortNameStatic(String shrotName) {
        return SHORT_MAPPER.findEnumClassWithShortName(shrotName);
    }
    
    public static Optional<Class> findNormalClassWithShortNameNoErrorStatic(String shrotName) {
        return SHORT_MAPPER.findNormalClassWithShortNameNoError(shrotName);
    }
    

    public static Optional<Class> findEnumClassWithShortNameNoErrorStatic(String shrotName) {
        return SHORT_MAPPER.findEnumClassWithShortNameNoError(shrotName);
    }
    
    public static Optional<Class> findClassWithShortNameStatic(String targetType, boolean isEnum) {
        return isEnum ? findEnumClassWithShortNameNoErrorStatic(targetType)
                : findNormalClassWithShortNameNoErrorStatic(targetType);
    }
    
    public static String findShortName(Class class1) {
        if (class1.isEnum()) {
            return SHORT_MAPPER.tryFindEnumClassShrotName(class1);
        } else {
            return SHORT_MAPPER.tryFindNormalClassShrotName(class1);
        }
    }
    
    public String tryFindEnumClassShrotName(Class class1) {
        return this.enumClassMap.inverse().getOrDefault(class1, class1.getName());
    }

    public String tryFindNormalClassShrotName(Class class1) {
        return this.normalClassMap.inverse().getOrDefault(class1, class1.getName());
    }

    public static boolean openShrotMapperServer() {
        return !SHORT_MAPPER.initDefault;
    }
    
    public static void customeRegisteClassMapper(
    		String targetType, 
    		Class<?> customerRegisteClass) {
    	if (customerRegisteClass.isEnum()) {
    		SHORT_MAPPER.customeRegisteEnumClassMapper(
    				targetType, 
    				customerRegisteClass);
    	} else {
    		SHORT_MAPPER.customeRegisteNormalClassMapper(
    				targetType, 
    				customerRegisteClass);
    	}
    }
    
    public void customeRegisteEnumClassMapper(
    		String targetType, 
    		Class<?> customerRegisteClass) {
		this.enumClassMap.put(
				targetType, 
				customerRegisteClass);
		log.info("Registe\t[" + targetType + "\t:\t" + customerRegisteClass + "]");
	}

	public void customeRegisteNormalClassMapper(
    		String targetType, 
    		Class<?> customerRegisteClass) {
    	this.normalClassMap.put(
    			targetType,
    			customerRegisteClass);
		log.info("Registe\t[" + targetType + "\t:\t" + customerRegisteClass + "]");
    }


    public static void main(String[] args) {
        ClassShortNameCache initShortMarkByClassName = 
                new ClassShortNameCache(); //Arrays.asList("com.mojin.ddd.base")
        Class findEnumClassWithShortName = initShortMarkByClassName.findEnumClassWithShortName("TestUserStatusEnum");
        System.out.println(findEnumClassWithShortName);
    }

}