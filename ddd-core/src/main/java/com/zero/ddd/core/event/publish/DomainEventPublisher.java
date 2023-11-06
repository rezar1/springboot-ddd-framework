package com.zero.ddd.core.event.publish;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import com.zero.ddd.core.model.DomainEvent;

import lombok.extern.slf4j.Slf4j;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time Oct 21, 2020 7:34:41 PM
 * @Desc 些年若许,不负芳华.
 *
 */
@Slf4j
public class DomainEventPublisher implements ApplicationContextAware {

    private static ApplicationContext applicationContext;

    public static <T extends DomainEvent> void publish(
    		T aDomiainEvent) {
        if (applicationContext != null) {
            applicationContext.publishEvent(aDomiainEvent);
        } else {
            log.warn("publish domain event fail because ApplicationContext was null,event={}", aDomiainEvent);
        }
    }

    @Override
    public void setApplicationContext(
    		ApplicationContext applicationContext) throws BeansException {
        log.info("DomainEventPublisher init");
        DomainEventPublisher.applicationContext = applicationContext;
    }

}
