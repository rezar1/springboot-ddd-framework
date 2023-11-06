
/**
 * Baijiahulian.com Inc. Copyright (c) 2014-2016 All Rights Reserved.
 */

package com.zero.helper.beanInvoker;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import lombok.extern.slf4j.Slf4j;

/**
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time Sep 30, 2016
 * @Desc this guy is too lazy, nothing left.
 */
@Slf4j
public class ByteSourceClassLoader extends ClassLoader {

    private static ByteSourceClassLoader loader = new ByteSourceClassLoader();

    private static Map<ClassLoader, ByteSourceClassLoader> loaderCache =
        new HashMap<ClassLoader, ByteSourceClassLoader>();

    private static Lock lock = new ReentrantLock();

    private ByteSourceClassLoader() {
    }

    private boolean userSuper;

    /**
     * @param cl
     */
    public ByteSourceClassLoader(ClassLoader cl) {
        super(cl);
        userSuper = true;
    }

    public Class<?> defineClass(String className, byte[] clazzBuff) {
        Class<?> retClass = findLoadedClass(className);
        if (retClass != null) {
            return retClass;
        }
        if (this.userSuper) {
            return super.defineClass(className, clazzBuff, 0, clazzBuff.length);
        } else {
            return this.defineClass(className, clazzBuff, 0, clazzBuff.length);
        }
    }

    public static Class<?> loadClass(String className, byte[] clazzBuff) {
        return loader.defineClass(className, clazzBuff);
    }

    public static Class<?> loadClass(ClassLoader cl, String className, byte[] classBuff) {
        ByteSourceClassLoader bsc = loaderCache.get(cl);
        if (bsc == null) {
            lock.lock();
            try {
                if ((bsc = loaderCache.get(cl)) == null) {
                    bsc = new ByteSourceClassLoader(cl);
                    loaderCache.put(cl, bsc);
                }
            } catch (Exception ex) {
                log.error("exception : {} ", ex);
            } finally {
                lock.unlock();
            }
        }
        if (bsc == null) {
            return null;
        }
        return bsc.defineClass(className, classBuff);
    }

}
