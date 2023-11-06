package com.zero.ddd.core.jpa.columnConverter;

import com.zero.ddd.core.classShortName.NeedCacheShortName;
import com.zero.helper.GU;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time Nov 20, 2020 5:00:18 PM
 * @Desc 些年若许,不负芳华.
 *
 */
@SuppressWarnings("rawtypes")
public class EnumToStringConverter extends AbstractObjToStringConverter<Enum, String> {

    private static final String enumDataFormat = "$TYPE:$Data";

    @Override
    public String convertToDatabaseColumn(Enum attribute) {
        if (attribute == null) {
            return "";
        }
        return enumDataFormat
                .replace("$TYPE", super.transTypeToShortName(attribute))
                .replace("$Data", attribute.name());
    }

    @SuppressWarnings("unchecked")
    @Override
    public Enum convertToEntityAttribute(String dbData) {
        if (GU.isNullOrEmpty(dbData)) {
            return null;
        }
        String[] split = dbData.split(":");
        if (split.length == 2) {
            String targetType = split[0];
            String enumName = split[1];
            return Enum.valueOf(
                    super.transTargetTypeToClass(
                            targetType, 
                            true), 
                    enumName);
        } else {
            return null;
        }
    }

    @NeedCacheShortName
    private static enum TestEnum {
        NAME, AGE
    }

    public static void main(String[] args) {
        EnumToStringConverter converter = new EnumToStringConverter();
        String convertToDatabaseColumn = converter.convertToDatabaseColumn(TestEnum.NAME);
        System.out.println(convertToDatabaseColumn);
        TestEnum convertToEntityAttribute = (TestEnum) converter.convertToEntityAttribute(convertToDatabaseColumn);
        System.out.println(convertToEntityAttribute);
    }

}