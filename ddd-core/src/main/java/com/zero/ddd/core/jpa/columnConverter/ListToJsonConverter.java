package com.zero.ddd.core.jpa.columnConverter;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.zero.helper.GU;
import com.zero.helper.JacksonUtil;

import lombok.Data;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time Nov 20, 2020 5:00:18 PM
 * @Desc 些年若许,不负芳华.
 *
 */
@SuppressWarnings("rawtypes")
public class ListToJsonConverter extends AbstractObjToStringConverter<List, String> {

    private static final String jsonDataFormat = "$TYPE($Data)";

    @SuppressWarnings("unchecked")
    @Override
    public String convertToDatabaseColumn(List attribute) {
        if (attribute == null || attribute.isEmpty()) {
            return "";
        }
        Object targetObjIns = attribute.stream().filter(Objects::nonNull).findAny().get();
        return jsonDataFormat
                .replace("$TYPE", super.transTypeToShortName(targetObjIns))
                .replace("$Data", JacksonUtil.obj2Str(attribute));
    }

    @SuppressWarnings("unchecked")
    @Override
    public List convertToEntityAttribute(String dbData) {
        if (GU.isNullOrEmpty(dbData)) {
            return new ArrayList<>(0);
        }
        Pattern pattern = Pattern.compile("(.*?)\\((.*?)\\)");
        Matcher matcher = pattern.matcher(dbData);
        if (matcher.matches()) {
            String targetType = matcher.group(1);
            String jsonData = matcher.group(2);
            return JacksonUtil.str2ListNoError(
                    jsonData, 
                    super.transTargetTypeToClass(
                            targetType, 
                            false));
        } else {
        	return new ArrayList<>(0);
        }
    }

    @Data
    private static class User {
        private String name = "Rezar";
    }

    public static void main(String[] args) {
        List<User> list = new ArrayList<User>();
        list.add(null);
        list.add(new User());
        ListToJsonConverter converter = new ListToJsonConverter();
        String convertToDatabaseColumn = converter.convertToDatabaseColumn(list);
        System.out.println(convertToDatabaseColumn);
        List convertToEntityAttribute = converter.convertToEntityAttribute(convertToDatabaseColumn);
        System.out.println(convertToEntityAttribute);
    }

}
