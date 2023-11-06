package com.zero.ddd.core.jpa.columnConverter;

import java.util.Optional;

import org.springframework.data.domain.ExampleMatcher.PropertyValueTransformer;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time Nov 29, 2020 12:30:24 AM
 * @Desc 些年若许,不负芳华.
 *
 */
public class EnumPropertyValueTransformer implements PropertyValueTransformer {
    
    public static final EnumPropertyValueTransformer transformer = new EnumPropertyValueTransformer();

    private EnumToStringConverter enumToStringConverter = new EnumToStringConverter();

    @SuppressWarnings("rawtypes")
    @Override
    public Optional<Object> apply(Optional<Object> t) {
        return t.filter(obj -> obj.getClass().isEnum())
        .map(obj -> this.enumToStringConverter.convertToDatabaseColumn((Enum) obj));
    }

}
