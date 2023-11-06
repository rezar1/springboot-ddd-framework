
 /**
 * Baijiahulian.com Inc.
 * Copyright (c) 2014-2016 All Rights Reserved.
 */
    
package com.zero.helper.beanInvoker;

import java.lang.reflect.Type;
import java.util.regex.Pattern;

import com.zero.helper.GU;

/**
 *  @say little Boy, don't be sad.
 *  @name Rezar
 *  @time Oct 26, 2016
 *  @Desc this guy is too lazy, nothing left.
 */

public class Str2NumberConverter extends Typeconverter{

        
    @Override
    public Object convert(String fieldName, Type sourceFieldType, Type destFieldType, Object value) {
        super.checkConvertTypes(sourceFieldType , destFieldType);
        
        Object setValue = value;
        if(value != null){
            if(String.class == sourceFieldType && (destFieldType instanceof Number || ((Class<?>)destFieldType).isPrimitive())){
                String strValue = (String) value;
                boolean isNumStr = checkIsNumStr(strValue);
                if(!isNumStr){
                    throw new IllegalArgumentException(String.format("%s not a digital string", value));
                }
                setValue = GU.getNumValueFromStr((Class<?>)destFieldType,strValue);
            }else if((sourceFieldType instanceof Number || ((Class<?>)sourceFieldType).isPrimitive()) && destFieldType == String.class){
                setValue = value.toString();
            }
        }
        return setValue;
    }
    
    private static final String NUM_REGEX = "-?\\d+((\\.\\d+)?)";

    /**
     * @param setValue
     * @return
     */
    private boolean checkIsNumStr(String setValue) {
        return Pattern.compile(NUM_REGEX).matcher(setValue).matches();
    }
    
    public static void main(String[] args) {
    }

}

    