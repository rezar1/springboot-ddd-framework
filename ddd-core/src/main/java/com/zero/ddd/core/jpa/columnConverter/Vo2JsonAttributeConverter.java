package com.zero.ddd.core.jpa.columnConverter;

import com.zero.ddd.core.classShortName.NeedCacheShortName;
import com.zero.helper.GU;
import com.zero.helper.JacksonUtil;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2021-09-27 04:01:06
 * @Desc 些年若许,不负芳华.
 *
 */
public class Vo2JsonAttributeConverter extends AbstractObjToStringConverter<Object, String> {

	@Override
	public String convertToDatabaseColumn(Object attribute) {
        if (attribute == null) {
            return "";
        }
        return objDataFormat
                .replace("$TYPE", super.transTypeToShortName(attribute))
                .replace("$Data", JacksonUtil.obj2Str(attribute));
    }

	private static final String objDataFormat = "$TYPE:$Data";

    @SuppressWarnings("unchecked")
    @Override
    public Object convertToEntityAttribute(String dbData) {
        if (GU.isNullOrEmpty(dbData)) {
            return null;
        }
        int indexOf = dbData.indexOf(":");
        if (indexOf == -1) {
        	return null;
        }
        String type = dbData.substring(0, indexOf);
        String voJson = dbData.substring(indexOf + 1);
        return JacksonUtil.str2ObjNoError(
                voJson,
                super.transTargetTypeToClass(
                		type, 
                        false));
    }

    @NeedCacheShortName
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    private static class TestClass {
        private String name;
        private int age;
    }

    public static void main(String[] args) {
        Vo2JsonAttributeConverter converter = new Vo2JsonAttributeConverter();
        TestClass testCla = new TestClass("Rezar", 0);
        String convertToDatabaseColumn = converter.convertToDatabaseColumn(testCla);
        System.out.println(convertToDatabaseColumn);
        TestClass convertToEntityAttribute = (TestClass) converter.convertToEntityAttribute(convertToDatabaseColumn);
        System.out.println(convertToEntityAttribute);
    }

}