package com.zero.ddd.core.classShortName;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.context.annotation.Import;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time Jul 11, 2020 6:13:46 PM
 * @Desc 些年若许,不负芳华.
 * 
 * 开启后系统会缓存指定包下面标注了
 * @NeedShortNameByClassName注解的类或者枚举以[类SimpleName:Class]存储的关系，
 * 方便反序列的时候以短名的格式找到对应的Class
 * 
 * 比如:数据库中存储TestUserStatusEnum的枚举，通过EnumToStringConverter的转换后，
 * 在数据库中存储的格式为:TestUserStatusEnum(NORMAL)
 * 在查询转换为原始类型的时候，便能通过缓存找到TestUserStatusEnum对应的类为:
 *       com.mojin.ddd.base.config.TestUserStatusEnum
 * 避免数据库存储过长的类路径前缀
 *
 */
@Documented
@Target({ ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
@Import({ ShortClassNameInitConfig.class })
public @interface EnableClassShortName {

    String[] packagePrefix() default {}; // 查找的包前缀

}