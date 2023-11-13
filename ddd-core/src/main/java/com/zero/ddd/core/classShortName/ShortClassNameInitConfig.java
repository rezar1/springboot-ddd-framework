package com.zero.ddd.core.classShortName;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.reflections.Reflections;
import org.reflections.scanners.TypeAnnotationsScanner;
import org.reflections.util.ConfigurationBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.type.AnnotationMetadata;

import com.zero.helper.GU;

import lombok.extern.slf4j.Slf4j;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time Nov 24, 2020 2:50:36 PM
 * @Desc 些年若许,不负芳华.
 *
 */
@Slf4j
@Configuration
public class ShortClassNameInitConfig implements ImportBeanDefinitionRegistrar {
	
    @Override
    public void registerBeanDefinitions(
            AnnotationMetadata importingClassMetadata,
            BeanDefinitionRegistry registry) {
        List<String> parsedScanPackages = 
        		this.parseScanPackages(importingClassMetadata);
        log.info("需要扫描标注[@NeedCacheShortName]注解的包:{}", parsedScanPackages);
        ClassShortNameCache.initStatic(
        		this.scanPackagesAndRetTargetClass(
        				parsedScanPackages));
    }
    
    private Set<Class<?>> scanPackagesAndRetTargetClass(
    		List<String> scanPackages) {
    	log.info(
    			"ShortClassNameInitConfig classLoader:{}", 
    			Thread.currentThread().getContextClassLoader());
    	ConfigurationBuilder configurationBuilder = 
    			new ConfigurationBuilder()
    			.addClassLoader(
    					Thread.currentThread().getContextClassLoader())
    			.addScanners(
    					new TypeAnnotationsScanner());
		for (String scanPackage : scanPackages) {
			configurationBuilder.forPackages(scanPackage);
    	}
		return 
				new Reflections(
						configurationBuilder)
				.getTypesAnnotatedWith(
						NeedCacheShortName.class);
    }

	private List<String> parseScanPackages(
			AnnotationMetadata importingClassMetadata) {
		return 
				 Optional.ofNullable(
			        		importingClassMetadata.getAnnotationAttributes(
			                        EnableClassShortName.class.getName()))
				 .map(configMap -> {
					 return 
							 configMap.get("packagePrefix");
				 })
				 .map(val -> (String[])val)
				 .filter(GU::notNullAndEmpty)
				 .map(Arrays::asList)
				 .orElseGet(() -> {
					 return 
							 Arrays.asList("");
				 });
	}

}