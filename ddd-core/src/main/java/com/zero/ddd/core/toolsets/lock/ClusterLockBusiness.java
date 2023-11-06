package com.zero.ddd.core.toolsets.lock;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2022-07-26 04:33:31
 * @Desc 些年若许,不负芳华.
 *
 */
@Retention(RUNTIME)
@Target(METHOD)
public @interface ClusterLockBusiness {
	
	/**
	 * 指定锁的作用范围，如单一服务多实例，指定为同一lockCenter,
	 * 	默认为IProdiveClusterLock.COMMON_LOCK_CENTER
	 * 
	 * @return
	 */
	String lockCenter() default "";
	
	/**
	 * 锁定的资源，默认为标注的方法名
	 * 
	 * @return
	 */
	String lockBusiness() default "";
	
	/**
	 * 锁获取超时时间
	 * 
	 * @return
	 */
	long timeout() default -1l;
	
	/**
	 * 锁获取超时单位
	 * 
	 * @return
	 */
	TimeUnit unit() default TimeUnit.MILLISECONDS;
	
	/**
	 * 当前服务节点获取到锁之后的占有模式
	 * @see LockHoldModel.DEFAULT 	默认模式
	 * @see HOLD_TIMES			占有指定次数后释放，其他服务节点可以获取锁
	 * @see HOLD_DURATION		占有多久后释放，其他服务节点可以获取锁
	 * @see HOLD_AWAYS			服务节点退出前一直占有
	 * @return
	 */
	LockHoldModel lockHoldModel() default LockHoldModel.DEFAULT;
	
	/**
	 * 
	 * @return
	 */
	long lockHoldUnit() default -1l;

}

