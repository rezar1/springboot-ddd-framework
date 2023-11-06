package com.zero.ddd.core.jpa.columnConverter;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
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
public class SetToJsonConverter extends AbstractObjToStringConverter<Set, String> {

    private static final String jsonDataFormat = "$TYPE($Data)";

    @SuppressWarnings("unchecked")
    @Override
    public String convertToDatabaseColumn(Set attribute) {
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
    public Set convertToEntityAttribute(String dbData) {
        if (GU.isNullOrEmpty(dbData)) {
            return new HashSet<>(0);
        }
        Pattern pattern = Pattern.compile("(.*?)\\((.*?)\\)");
        Matcher matcher = pattern.matcher(dbData);
        if (matcher.matches()) {
            String targetType = matcher.group(1);
            String jsonData = matcher.group(2);
            return new HashSet<>(
            		JacksonUtil.str2ListNoError(
                            jsonData, 
                            super.transTargetTypeToClass(
                                    targetType, 
                                    false)));
        } else {
        	return new HashSet<>(0);
        }
    }

    @Data
    private static class User {
        private String name = "Rezar";
    }

}
