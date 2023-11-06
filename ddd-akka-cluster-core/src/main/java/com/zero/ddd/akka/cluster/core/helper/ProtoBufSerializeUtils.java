package com.zero.ddd.akka.cluster.core.helper;


import java.io.IOException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.zero.helper.GU;

import io.protostuff.Input;
import io.protostuff.LinkedBuffer;
import io.protostuff.Output;
import io.protostuff.Pipe;
import io.protostuff.ProtostuffIOUtil;
import io.protostuff.Schema;
import io.protostuff.WireFormat.FieldType;
import io.protostuff.runtime.DefaultIdStrategy;
import io.protostuff.runtime.Delegate;
import io.protostuff.runtime.RuntimeEnv;
import io.protostuff.runtime.RuntimeSchema;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ProtoBufSerializeUtils {

	/** 时间戳转换Delegate，解决时间戳转换后错误问题 @author jiujie 2016年7月20日 下午1:52:25 */
	private final static Delegate<Timestamp> TIMESTAMP_DELEGATE = new TimestampDelegate();

	private final static DefaultIdStrategy idStrategy = ((DefaultIdStrategy) RuntimeEnv.ID_STRATEGY);

//	private static LoadingCache<ClassLoader, SpecialClassPathClassLoader> specialClassPathClassLoaderCache;
//
	static {
		idStrategy.registerDelegate(TIMESTAMP_DELEGATE);
//		specialClassPathClassLoaderCache = 
//				SimpleCacheBuilder.instance(
//						SpecialClassPathClassLoader::new);
	}
	
	public static <T> void registerDelegate(Delegate<T> delegate) {
		idStrategy.registerDelegate(delegate);
	}

	/**
	 * /** 缓存Schema
	 */
	private static Map<Class<?>, Schema<?>> schemaCache = new ConcurrentHashMap<Class<?>, Schema<?>>();
	
	private static List<SpecialTypeNameConverter> specialTypeNameConverters = new ArrayList<>();
	
	public static void configSpecialTypeNameConverter(
			SpecialTypeNameConverter converter) {
		specialTypeNameConverters.add(converter);
		log.info("configSpecialTypeNameConverter:{}", converter);
	}

	@SuppressWarnings("unchecked")
	public static <T> byte[] serialize(T obj) {
		if (obj == null) {
			throw new NullPointerException();
		}
		Class<T> clazz = (Class<T>) obj.getClass();
		Schema<T> schema = getSchema(clazz);
		LinkedBuffer buffer = 
				LinkedBuffer.allocate(
						LinkedBuffer.DEFAULT_BUFFER_SIZE);
		try {
			return ProtostuffIOUtil.toByteArray(obj, schema, buffer);
		} finally {
			buffer.clear();
		}
	}

	public static <T> T deserialize(byte[] data, Class<T> clazz) {
//		ClassLoader current = 
//				Thread.currentThread().getContextClassLoader();
		try {
//			Thread.currentThread()
//			.setContextClassLoader(
//					specialClassPathClassLoaderCache.getUnchecked(current));
			Schema<T> schema = getSchema(clazz);
			T obj = schema.newMessage();
			ProtostuffIOUtil.mergeFrom(data, obj, schema);
			return obj;
		} finally {
//			Thread.currentThread().setContextClassLoader(current);
		}
	}
	
	@SuppressWarnings("unchecked")
	private static <T> Schema<T> getSchema(Class<T> clazz) {
		Schema<T> schema = (Schema<T>) schemaCache.get(clazz);
		if (schema == null) {
			// 这个schema通过RuntimeSchema进行懒创建并缓存
			// 所以可以一直调用RuntimeSchema.getSchema(),这个方法是线程安全的
			schema = RuntimeSchema.getSchema(clazz, idStrategy);
			if (schema != null) {
				schemaCache.put(clazz, schema);
			}
		}
		return schema;
	}
	
	public static String converTo(String name) {
		return specialTypeNameConverters.stream()
				.map(convertor -> convertor.converTo(name))
				.filter(GU::notNullAndEmpty)
				.findFirst()
				.orElse(null);
	}
	
	static class SpecialClassPathClassLoader extends ClassLoader {
		private Map<String, Class<?>> loaded = new HashMap<>();

		public SpecialClassPathClassLoader(
				ClassLoader parent) {
			super(parent);
		}

		@Override
		protected Class<?> findClass(String name) throws ClassNotFoundException {
			if (loaded.containsKey(name)) {
				return this.loaded.get(name);
			}
			Class<?> loadClass = this.load(name);
			if (loadClass != null) {
				return loadClass;
			}
			String convertedName = converTo(name);
			if (GU.notNullAndEmpty(convertedName)
					&& !convertedName.equals(name)) {
				loadClass = this.load(convertedName);
				if (loadClass != null) {
					loaded.put(name, loadClass);
					return loadClass;
				}
			}
			throw new ClassNotFoundException(name);
		}
		
		private Class<?> load(String name){
			try {
				return this.getParent().loadClass(name);
			} catch (ClassNotFoundException e) {
				return null;
			}
		}
		
	}
	
	public static interface SpecialTypeNameConverter {
		public String converTo(String sourceTypeName);
	}

	public static class TimestampDelegate implements Delegate<Timestamp> {

		public FieldType getFieldType() {
			return FieldType.FIXED64;
		}

		public Class<?> typeClass() {
			return Timestamp.class;
		}

		public Timestamp readFrom(Input input) throws IOException {
			return new Timestamp(input.readFixed64());
		}

		public void writeTo(Output output, int number, Timestamp value, boolean repeated) throws IOException {
			output.writeFixed64(number, value.getTime(), repeated);
		}

		public void transfer(Pipe pipe, Input input, Output output, int number, boolean repeated) throws IOException {
			output.writeFixed64(number, input.readFixed64(), repeated);
		}

	}
	
	/**
	 * protobuf 序列化会过滤掉null对象, 导致数组缺失某个位置的空值，后面的值往前补位;
	 * 数据发送方可以使用OBJECT_NULL占位，数据解析方判断是否equals(OBJECT_NULL)进行替换为null;
	 */
	public static final NULL_OBJ OBJECT_NULL = new NULL_OBJ();
	
	public static Object[] replaceObjNullWithNull(Object[] args) {
		if (GU.notNullAndEmpty(args)) {
			for (int index = 0; index < args.length; index ++) {
				if (args[index].equals(ProtoBufSerializeUtils.OBJECT_NULL)) {
					args[index] = null;
				}
			}
		}
		return args;
	}
	
	public static Object[] replaceNullWithObject(Object[] args) {
		if (!GU.isNullOrEmpty(args)) {
			for (int index = 0; index < args.length; index++) {
				if (args[index] == null) {
					args[index] = OBJECT_NULL;
				}
			}
		}
		return args;
	}
	
	public static class NULL_OBJ {
		private NULL_OBJ() {}

		@Override
		public int hashCode() {
			return 28;
		}

		@Override
		public boolean equals(Object obj) {
			return obj != null
					&& obj.getClass() == NULL_OBJ.class;
		}
		
		
		
	}

}