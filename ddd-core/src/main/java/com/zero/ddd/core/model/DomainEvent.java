package com.zero.ddd.core.model;

import java.util.Date;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time Oct 21, 2020 6:08:12 PM
 * @Desc 些年若许,不负芳华.
 *
 */
public interface DomainEvent {

    default int eventVersion() {
        return 1;
    }

    default Date occurredOn() {
        return new Date();
    }
    
}
