
/**
 * Baijiahulian.com Inc. Copyright (c) 2014-2016 All Rights Reserved.
 */

package com.zero.helper.beanInvoker;

import java.lang.reflect.Field;

/**
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time Oct 19, 2016
 * @Desc this guy is too lazy, nothing left.
 */

public interface FieldFilter {

    public boolean needFilter(Field field);

}
