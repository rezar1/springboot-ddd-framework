package com.zero.helper;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class JacksonUtil {
	
    private static final ObjectMapper mapper;

    static {
        mapper = new ObjectMapper();
        mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, true);
        mapper.configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true);
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.setSerializationInclusion(Include.NON_NULL);
        mapper.registerModule(
        		new JavaTimeModule())
        .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, true)
        .configure(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE, false);
    }
    
    @SuppressWarnings("unchecked")
	public static Map<String, Object> jsonToMap(
    		String jsonData) throws Exception {
    	ObjectMapper mapper = new ObjectMapper();
    	return mapper.readValue(jsonData, Map.class);
    }
    
    public static ObjectWriter pretty() {
    	return mapper.writerWithDefaultPrettyPrinter();
    }
    
    public static final String prettyPrint(Object o) {
		try {
			if (o == null) {
				return null;
			}
			return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(o);
		} catch (Exception e) {
			return null;
		}
	}
    
    public static final <V,T extends List<V>> String list2Str(
            TypeReference<T> rootReference,
            T list) {
        try {
            if (list == null 
                    || rootReference == null) {
                return null;
            }
            return mapper.writerFor(
                    rootReference)
                    .writeValueAsString(list);
        } catch (IOException e) {
            log.error("errorMsg:{}", e);
            return null;
        }
    }
    
    public static final String obj2Str(Object o) {
        try {
            if (o == null) {
                return null;
            }
            return mapper.writeValueAsString(o);
        } catch (IOException e) {
            log.error("errorMsg:{}", e);
            return null;
        }
    }

    public static final void writeObj(OutputStream out, Object value)
            throws JsonGenerationException, JsonMappingException, IOException {
        mapper.writeValue(out, value);
    }

    public static final <T> T str2Obj(String s, Class<T> valueType)
            throws JsonParseException, JsonMappingException, IOException {
        JavaType javaType = getJavaType(valueType, null);
        return mapper.readValue(s, javaType);
    }

    public static final <T> T str2ObjNoError(String s, Class<T> valueType) {
        JavaType javaType = getJavaType(valueType, null);
        try {
            return mapper.readValue(s, javaType);
        } catch (Exception e) {
            e.printStackTrace();
            log.warn("wrong json:[{}] and valueType:[{}]", s, valueType);
            log.error("error while str2ObjNoError:{}", s);
            return null;
        }
    }

    public static final <T> Optional<T> str2ObjOpt(String s, Class<T> valueType) {
        return Optional.ofNullable(str2ObjNoError(s, valueType));
    }

    @SuppressWarnings("deprecation")
    protected static JavaType getJavaType(Type type, Class<?> contextClass) {
        return (contextClass != null) ? mapper.getTypeFactory().constructType(type, contextClass)
                : mapper.constructType(type);
    }

    public static final <T> T str2Obj(String s, TypeReference<T> valueType)
            throws JsonParseException, JsonMappingException, IOException {
        return mapper.readValue(s, valueType);
    }

    public static final <T> List<T> str2List(String s, Class<T> valueType)
            throws JsonParseException, JsonMappingException, IOException {
        JavaType javaType = mapper.getTypeFactory().constructParametricType(List.class, valueType);
        return mapper.readValue(s, javaType);
    }

    public static final <T> List<T> str2ListNoError(String s, Class<T> valueType) {
        JavaType javaType = mapper.getTypeFactory().constructParametricType(List.class, valueType);
        try {
            return mapper.readValue(s, javaType);
        } catch (Exception e) {
            log.warn("wrong json:[{}] and valueType:[{}]", s, valueType);
            log.error("error while str2ObjNoError:{}", s);
            return null;
        }
    }

    public static final <T> T readObj(InputStream in, Class<T> valueType)
            throws JsonParseException, JsonMappingException, IOException {
        return mapper.readValue(in, valueType);
    }

    @SuppressWarnings("unchecked")
    public static final <T> T readObj(InputStream in, JavaType valueType)
            throws JsonParseException, JsonMappingException, IOException {
        return (T) mapper.readValue(in, valueType);
    }
    
    public static final <T> T readVal(String json, TypeReference<T> typeRef) 
    		throws JsonMappingException, JsonProcessingException {
    	return mapper.readValue(json, typeRef);
    }
    
    public static final <T> Optional<T> readValNoError(
    		String json) {
    	TypeReference<T> typeRef = new TypeReference<T>() {
		};
    	T res = null;
    	try {
    		res = readVal(json, typeRef);
		} catch (Exception e) {
            log.warn("wrong json:[{}] and valueType:[{}]", json, typeRef.getType());
            log.error("error while str2ObjNoError:{}", e);
        }
    	return Optional.ofNullable(res);
    }

}
