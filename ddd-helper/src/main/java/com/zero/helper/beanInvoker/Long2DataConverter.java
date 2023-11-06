
/**
 * Baijiahulian.com Inc. Copyright (c) 2014-2016 All Rights Reserved.
 */

package com.zero.helper.beanInvoker;

import java.lang.reflect.Type;
import java.sql.Timestamp;
import java.util.Date;


/**
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time Oct 26, 2016
 * @Desc this guy is too lazy, nothing left.
 */

public class Long2DataConverter extends Typeconverter {

    @Override
    public Object convert(String fieldName, Type sourceFieldType, Type destFieldType, Object value) {
        super.checkConvertTypes(sourceFieldType, destFieldType);
        Object setValue = value;
        if (value != null) {
            if (matchNumberType(sourceFieldType)) {
                if (destFieldType == Date.class) {
                    setValue = new Date((long) value);
                } else if (destFieldType == Timestamp.class) {
                    setValue = new Timestamp((long) value);
                } else if (destFieldType == java.sql.Date.class) {
                    setValue = new java.sql.Date((long) value);
                }
            } else if (matchDateType(sourceFieldType)) {
                if (sourceFieldType == Date.class) {
                    setValue = ((Date) value).getTime();
                } else if (sourceFieldType == Timestamp.class) {
                    setValue = ((Timestamp) value).getTime();
                } else if (sourceFieldType == java.sql.Date.class) {
                    setValue = ((java.sql.Date) value).getTime();
                }
                if (destFieldType == int.class || destFieldType == Integer.class) {
                    return ((Long) setValue).intValue();
                }
            }
        }
        return setValue;
    }

    /**
     * @param sourceFieldType
     * @return
     */
    private boolean matchDateType(Type sourceFieldType) {
        return sourceFieldType == Date.class || sourceFieldType == java.sql.Date.class
            || sourceFieldType == Timestamp.class;
    }

    /**
     * @param sourceFieldType
     * @return
     */

    private boolean matchNumberType(Type sourceFieldType) {
        return sourceFieldType == Long.class || sourceFieldType == long.class || sourceFieldType == Integer.class
            || sourceFieldType == int.class;
    }

}
