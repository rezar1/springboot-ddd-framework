package com.zero.ddd.core.domainRegistry;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time Oct 22, 2020 4:38:37 PM
 * @Desc 些年若许,不负芳华.
 *
 */
public class DomainRegistry implements ApplicationContextAware {

    private static ApplicationContext applicationContext;

    public static <T> T bean(Class<T> clazz) {
        if (applicationContext == null) {
            throw new RuntimeException("bean.not.exist");
        }
        return applicationContext.getBean(clazz);
    }
    
    public static <T> T operation(Class<T> clazz) {
        return bean(clazz);
    }

    public static <T> T service(Class<T> clazz) {
        return bean(clazz);
    }

    public static <T> T repo(Class<T> clazz) {
        return bean(clazz);
    }

    public static <T> List<T> allBeans(Class<T> clazz) {
        if (applicationContext == null) {
            throw new RuntimeException("bean.not.exist");
        }
        Map<String, T> beans = applicationContext.getBeansOfType(clazz);
        List<T> t = beans.values().stream().collect(Collectors.toList());

        if (t == null) {
            throw new RuntimeException("bean.not.exist");
        }
        return t;
    }

    public static <T> Map<String, T> beanMap(Class<T> clazz) {
        if (applicationContext == null) {
            throw new RuntimeException("bean.not.exist");
        }
        Map<String, T> beans = applicationContext.getBeansOfType(clazz);
        if (beans == null) {
            throw new RuntimeException("bean.not.exist");
        }
        return beans;
    }

    @Override
    public void setApplicationContext(
    		ApplicationContext applicationContext) throws BeansException {
        if (DomainRegistry.applicationContext == null) {
            DomainRegistry.applicationContext = applicationContext;
        }
    }

    public static boolean online() {
        return DomainRegistry.applicationContext != null;
    }
}
