package com.zero.ddd.core.classShortName;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time Jul 11, 2020 6:13:46 PM
 * @Desc 些年若许,不负芳华.
 *
 */
@Documented
@Target({ ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
public @interface NeedCacheShortName {
    String shortName() default "";
}
