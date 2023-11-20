package com.zero.ddd.core.jpa.columnConverter.annotations;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import javax.persistence.Convert;

import com.zero.ddd.core.jpa.columnConverter.SetToJsonConverter;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2023-11-20 03:57:19
 * @Desc 些年若许,不负芳华.
 *
 */
@Target(FIELD)
@Retention(RUNTIME)
@Convert(converter = SetToJsonConverter.class)
public @interface Set2JsonConverter {

}