
/**
 * Baijiahulian.com Inc. Copyright (c) 2014-2016 All Rights Reserved.
 */

package com.zero.helper.beanInvoker;

/**
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time Oct 12, 2016
 * @Desc this guy is too lazy, nothing left.
 */

public interface Invoker<T> {

    /**
     * 调用方法
     * 
     * @param host 执行对象
     * @param args 执行参数
     * @return
     */
    Object invoke(T host, Object...args);

}
