package com.zero.helper;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2023-04-27 08:21:21
 * @Desc 些年若许,不负芳华.
 *
 */
public class LocalDateTimeSerializer extends JsonSerializer<LocalDateTime> {
    
    private static final DateTimeFormatter formatter = 
    		DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    @Override
    public void serialize(
    		LocalDateTime value, 
    		JsonGenerator gen,
    		SerializerProvider serializers) throws IOException {
        gen.writeString(formatter.format(value));
    }
}