package com.zero.ddd.core.jpa.columnConverter;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.reflections.ReflectionUtils;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.zero.ddd.core.classShortName.NeedCacheShortName;
import com.zero.helper.GU;
import com.zero.helper.JacksonUtil;
import com.zero.helper.beanInvoker.BeanInvokeUtils;
import com.zero.helper.beanInvoker.BeanMethodInvoke;
import com.zero.helper.beanInvoker.Invoker;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time Nov 20, 2020 5:00:18 PM
 * @Desc 些年若许,不负芳华.
 *
 */
@SuppressWarnings("rawtypes")
public class ListToSingleValueJsonConverter extends AbstractObjToStringConverter<List, String> {

    private static final String jsonDataFormat = "$TYPE($Data)";
    
    private static LoadingCache<Class, RWInvoker> classSingleFieldReaderCache = 
            CacheBuilder.newBuilder().build(new CacheLoader<Class, RWInvoker>() {
        @SuppressWarnings("unchecked")
        @Override
        public RWInvoker load(Class targetClass) throws Exception {
            BeanMethodInvoke beanMethodInovker = 
            		BeanInvokeUtils.findBeanMethodInovker(targetClass);
            return ReflectionUtils.getFields(
                    targetClass, 
                    field -> field.getAnnotation(MarkSingleValueField.class) != null)
            .stream()
            .map(field -> new RWInvoker(beanMethodInovker, field))
            .findAny()
            .orElse(RWInvoker.NULL_INVOKER);
        }
    });
    
    @SuppressWarnings("unchecked")
    @Override
    public String convertToDatabaseColumn(List attribute) {
        if (attribute == null || attribute.isEmpty()) {
            return "";
        }
        Object targetObjIns = attribute.stream().filter(Objects::nonNull).findAny().get();
        return jsonDataFormat
                .replace("$TYPE", super.transTypeToShortName(targetObjIns))
                .replace("$Data", paseToSingleValueList(targetObjIns, attribute));
    }

    /*
     * 将主键类列表序列化为:["PK1","PK2"]
     */
    @SuppressWarnings("unchecked")
    private String paseToSingleValueList(Object targetObjIns, List attribute) {
        RWInvoker invoker = classSingleFieldReaderCache.getUnchecked(targetObjIns.getClass());
        if (!invoker.isExists()) {
            return JacksonUtil.obj2Str(attribute);
        } else {
            return JacksonUtil.obj2Str(
                    attribute.stream()
                    .map(invoker::read)
                    .collect(Collectors.toList()));
        }
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
            Class transTargetTypeToClass = 
            		super.transTargetTypeToClass(
            				targetType, 
            				false);
            RWInvoker invoker = classSingleFieldReaderCache
                    .getUnchecked(transTargetTypeToClass);
            if (invoker.isExists()) {
                return invoker.parseToList(jsonData);
            }
            return JacksonUtil.str2ListNoError(
                    jsonData, 
                    super.transTargetTypeToClass(
                            targetType, 
                            false));
        } else {
        	return new ArrayList<>(0);
        }
    }
    
    @NoArgsConstructor
    private static class RWInvoker{
        
        static final RWInvoker NULL_INVOKER = new RWInvoker();
        
        private BeanMethodInvoke beanMethodInovker;
        private Invoker readerInvoker;
        private Invoker writerInvoker;
        private Class fieldType;
        
        public RWInvoker(BeanMethodInvoke beanMethodInovker, Field field) {
            String fieldName = field.getName();
            this.fieldType = field.getType();
            this.beanMethodInovker = beanMethodInovker;
            this.readerInvoker = beanMethodInovker.getFieldReader(fieldName);
            this.writerInvoker = beanMethodInovker.getFieldWriter(fieldName);
        }
        
        @SuppressWarnings("unchecked")
        public List parseToList(String jsonData) {
            List str2ListNoError = JacksonUtil.str2ListNoError(jsonData, this.fieldType);
            return (List) str2ListNoError.stream().map(this::write).collect(Collectors.toList());
        }

        public boolean isExists() {
            return this != NULL_INVOKER;
        }

        @SuppressWarnings("unchecked")
        public Object read(Object sourceObj) {
            if (sourceObj == null) {
                return null;
            }
            return this.readerInvoker.invoke(sourceObj);
        }
        
        @SuppressWarnings("unchecked")
        public Object write(Object val) {
            Object instanceByDefault = beanMethodInovker.instanceByDefault();
            this.writerInvoker.invoke(instanceByDefault, val);
            return instanceByDefault;
        }
        
    }
    

    @Data
    @NeedCacheShortName
    public static class User {
        @MarkSingleValueField
        private String name = "Rezar";
    }

    public static void main(String[] args) {
        List<User> list = new ArrayList<User>();
        list.add(null);
        list.add(new User());
        ListToSingleValueJsonConverter converter = new ListToSingleValueJsonConverter();
        String convertToDatabaseColumn = converter.convertToDatabaseColumn(list);
        System.out.println(convertToDatabaseColumn);
        List convertToEntityAttribute = converter.convertToEntityAttribute(convertToDatabaseColumn);
        System.out.println(convertToEntityAttribute);
    }
    
    @Retention(RUNTIME)
    @Target(FIELD)
    /**
     * 
     * @say little Boy, don't be sad.
     * @name Rezar
     * @time Nov 26, 2020 4:50:48 PM
     * @Desc 些年若许,不负芳华.
     * 
     *  标记一个属性是当前值对象中唯一需要被序列化的属性
     *  
     *  如: User{ String name;}
     *  1. @see ListToJsonConverter 会序列化为: User([{name:'xxxx'}])
     *  2.标注该注解并配合 @see ListToSingleValueJsonConverter,会序列化为 User(['xxxx'])
     *
     */
    public static @interface MarkSingleValueField {

    }

}
