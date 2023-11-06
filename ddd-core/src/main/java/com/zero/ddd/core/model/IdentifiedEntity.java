package com.zero.ddd.core.model;

import javax.persistence.Column;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.MappedSuperclass;

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
 * @time Oct 21, 2020 4:52:45 PM
 * @Desc 些年若许,不负芳华.
 * 
 * 层超类型，向客户端隐藏标识信息
 *
 */
@Getter
@Setter(AccessLevel.PROTECTED)
@Accessors(chain = true)
@ToString
@EqualsAndHashCode(callSuper = true, of = "id")
@MappedSuperclass
public class IdentifiedEntity extends ConcurrencySafeEntity {

    @Id
    @Column
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id = -1l;

}
