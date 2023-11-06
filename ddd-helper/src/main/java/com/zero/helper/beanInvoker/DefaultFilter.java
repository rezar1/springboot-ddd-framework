
/**
 * Baijiahulian.com Inc. Copyright (c) 2014-2016 All Rights Reserved.
 */

package com.zero.helper.beanInvoker;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time Oct 19, 2016
 * @Desc this guy is too lazy, nothing left.
 */

public class DefaultFilter implements FieldFilter, MethodFilter {

    @Override
    public boolean needFilter(Method method) {
        return method.isAnnotationPresent(InvokeExclude.class);
    }

    @Override
    public boolean needFilter(Field field) {
        return Modifier.isStatic(field.getModifiers()) || field.isAnnotationPresent(InvokeExclude.class);
    }

}
