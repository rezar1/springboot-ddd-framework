
/**
 * Baijiahulian.com Inc. Copyright (c) 2014-2016 All Rights Reserved.
 */

package com.zero.helper.beanInvoker;

import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time Oct 20, 2016
 * @Desc this guy is too lazy, nothing left.
 */
public abstract class Typeconverter {

    private static final List<Typeconverter> defaultConverters =
        Arrays.asList(new Long2DataConverter(), new Str2NumberConverter());

    public final Object superConvert(String fieldName, Type sourceFieldType, Type destFieldType, Object value) {
        if(value == null || (sourceFieldType == destFieldType)){
            return value;
        }
        Collection<Typeconverter> convterters = this.getConvterters();
        Object retValue = null;
        retValue = this.convert(fieldName, sourceFieldType, destFieldType, value);
        if (retValue != null && !retValue.equals(value)) {
            return retValue;
        }
        for (Typeconverter typeConvterter : convterters) {
            retValue = typeConvterter.convert(fieldName, sourceFieldType, destFieldType, value);
            if (retValue != null && !retValue.equals(value)) {
                return retValue;
            }
        }
        return value;
    }

    public abstract Object convert(String fieldName, Type sourceFieldType, Type destFieldType, Object value);

    /**
     * @param sourceFieldType
     * @param destFieldType
     */
    public void checkConvertTypes(Type sourceFieldType, Type destFieldType) {
        if (sourceFieldType == null) {
            throw new IllegalArgumentException(String.format("sourceFieldType :%s is null ,can not complete convert "));
        } else if (destFieldType == null) {
            throw new IllegalArgumentException(String.format("destFieldType :%s is null ,can not complete convert "));
        }
    }

    protected Collection<Typeconverter> getConvterters() {
        return Collections.unmodifiableCollection(defaultConverters);
    }

}
