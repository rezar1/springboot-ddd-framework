package com.zero.ddd.core.model;

import java.util.Date;

import javax.persistence.Column;
import javax.persistence.MappedSuperclass;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import javax.persistence.Version;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time Oct 21, 2020 4:51:11 PM
 * @Desc 些年若许,不负芳华.
 *
 */
@Getter
@Setter(AccessLevel.PROTECTED)
@Accessors(chain = true)
@ToString
@EqualsAndHashCode(callSuper = false)
@MappedSuperclass
public class ConcurrencySafeEntity extends AssertionConcern {

    // 乐观控制版本号
    @Column
    @Version
    private Integer version;
    
    @Column
    @CreationTimestamp
    private Date createdAt;
    
    @Column
    @UpdateTimestamp
    @Temporal(TemporalType.TIMESTAMP)
    private Date updatedAt;

}
