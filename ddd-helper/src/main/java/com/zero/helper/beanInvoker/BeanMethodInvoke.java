
/**
 * Baijiahulian.com Inc. Copyright (c) 2014-2016 All Rights Reserved.
 */

package com.zero.helper.beanInvoker;

import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.objectweb.asm.Type;

import com.zero.helper.GU;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

/**
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time Oct 12, 2016
 * @Desc 针对某个对象创建的方法快速调用器
 */
@Slf4j
@Data
public class BeanMethodInvoke<T> {

	private int hasCodeAsKey;
	private Class<?> hostClass;
	private String hostClassName;

	private static DefaultFilter defaultFilter = new DefaultFilter();

	private FieldFilter fieldFilter;
	private MethodFilter methodFilter;

	public static final Object[] EMPTY_ARGS = new Object[] {};

	public static final List<String> exclude_methods = Arrays.asList("main([Ljava/lang/String;)V",
			"toString()Ljava/lang/String;", "equals(Ljava/lang/Object;)Z", "hashCode()I", "wait(JI)V", "wait(J)V",
			"wait()V", "getClass()Ljava/lang/Class;", "notify()V", "notifyAll()V");

	public List<String> fieldNames = new ArrayList<>();

	private Map<String, java.lang.reflect.Type> fieldTypeMapper = new HashMap<>();

	/**
	 * 针对某个对象进行调用器
	 */
	private Map<Integer, Invoker<T>> publicMethodInvoke = new HashMap<Integer, Invoker<T>>();
	private InstanceInvoker<T> instanceMethod;

	public BeanMethodInvoke(Class<?> hostClass, FieldFilter fieldFilter, MethodFilter methodFilter) {
		if (hostClass == null) {
			return;
		}
		ClassLoader cl = hostClass.getClassLoader();
		log.debug("classLoader : {} ", cl);
		if (cl == null) {
			cl = ClassLoader.getSystemClassLoader();
		}
		String classLoaderDesc = Type.getDescriptor(cl.getClass());
		String classDesc = Type.getDescriptor(hostClass);
		this.hasCodeAsKey = Arrays.hashCode(new String[] { classLoaderDesc, classDesc });
		log.debug("className:{} with classLoad:{} , generate hashcode :{} ", classDesc, classLoaderDesc,
				this.hasCodeAsKey);
		this.hostClass = hostClass;
		this.hostClassName = this.hostClass.getName();
		this.fieldFilter = (fieldFilter == null ? defaultFilter : fieldFilter);
		this.methodFilter = (methodFilter == null ? defaultFilter : methodFilter);
		this.generatorMethodInvoke();
	}

	public BeanMethodInvoke(Class<?> hostClass) {
		this(hostClass, defaultFilter, defaultFilter);
	}

	public int getBeanKey() {
		return this.hasCodeAsKey;
	}

	public <R> R getInvokeResult(String keyName, Object... args) {
		throw new UnsupportedOperationException("Has not been implemented");
	}

	/**
	 * 生成当前对象的所有方法的快速调用器
	 */
	private void generatorMethodInvoke() {
		// 使用反射获取所有公有的方法
		Method[] methods = this.hostClass.getMethods();
		if (GU.isNullOrEmpty(methods)) {
			return;
		}
		Map<String, TwoTuple<String, Boolean>> fieldVisitMethod = getFieldVisitMethod(this.hostClass);
		Class<?> tmpClass = this.hostClass;
		do {
			Field[] fields = tmpClass.getDeclaredFields();
			for (Field field : fields) {
				if (!this.fieldFilter.needFilter(field)) {
					fieldNames.add(field.getName());
				}
				fieldTypeMapper.put(field.getName(), field.getType());
			}
			tmpClass = tmpClass.getSuperclass();
		} while (tmpClass != null && !tmpClass.equals(Object.class));
		for (Method method : methods) {
			boolean methodNeedFilter = this.methodFilter.needFilter(method);
			String methodDesc = Type.getMethodDescriptor(method);
			if (methodNeedFilter || exclude_methods.contains(method.getName() + methodDesc)) {
				continue;
			}
			TwoTuple<String, Boolean> propertyDescriptor = fieldVisitMethod.get(method.getName());
			if (propertyDescriptor != null) {
				log.debug(method.getName() + "-----" + propertyDescriptor.first);
				if (!fieldNames.contains(propertyDescriptor.first)) {
					continue;
				}
			}
			Integer fieldVisitKey = null;
			Integer methodId = SpecialNameRegistry.getInstance().getMethodIdAndAllocate(this.hostClassName,
					method.getName(), SpecialNameRegistry.argumentTypesToString(method.getParameterTypes()));
			log.debug("method.getName is :{}", method.getName());
			Invoker<T> invoker = createInvoker(method);
			if (propertyDescriptor != null) {
				if (propertyDescriptor.second) {
					fieldVisitKey = ("R" + propertyDescriptor.first).hashCode();
				} else {
					fieldVisitKey = ("W" + propertyDescriptor.first).hashCode();
				}
				if (!this.publicMethodInvoke.containsKey(fieldVisitKey)) {
					this.publicMethodInvoke.put(fieldVisitKey, invoker);
				}
			} else {
				this.publicMethodInvoke.put(methodId, invoker);
			}
		}
		initInstanceMethod();
	}

	/**
	 * 
	 */
	private void initInstanceMethod() {
		this.instanceMethod = InvokerCreateor.createInstanceInvoker(getHostClass());
	}

	public T instanceByDefault() {
		return this.instanceMethod.instance();
	}

	/**
	 * @param method
	 * @return
	 */
	private Invoker<T> createInvoker(Method method) {
		log.debug("method is : " + method.getName());
		return InvokerCreateor.createInvoker(method);
	}

	public static Map<String, TwoTuple<String, Boolean>> getFieldVisitMethod(Class<?> sourceClass) {
		List<PropertyDescriptor> classPropertyDescriptors = getClassPropertyDescriptors(sourceClass);
		Map<String, TwoTuple<String, Boolean>> ret = new HashMap<>();
		for (PropertyDescriptor pd : classPropertyDescriptors) {
			Method readMethod = pd.getReadMethod();
			if (readMethod != null) {
				ret.put(readMethod.getName(), TupleUtil.tuple(pd.getName(), true));
			}
			Method writeMethod = pd.getWriteMethod();
			if (writeMethod != null) {
				ret.put(writeMethod.getName(), TupleUtil.tuple(pd.getName(), false));
			}
		}
		return ret;
	}

	public static Map<String, PropertyDescriptor> getClassPropertyDescMap(Class<?> sourceClass, boolean isMethodName) {
		List<PropertyDescriptor> classPropertyDescriptors = getClassPropertyDescriptors(sourceClass);
		Map<String, PropertyDescriptor> retMap = new HashMap<>(classPropertyDescriptors.size());
		for (PropertyDescriptor pd : classPropertyDescriptors) {
			if (isMethodName) {
				retMap.put(pd.getReadMethod().getName(), pd);
				retMap.put(pd.getWriteMethod().getName(), pd);
			} else {
				retMap.put(pd.getName(), pd);
			}
		}
		return retMap;
	}

	public static List<PropertyDescriptor> getClassPropertyDescriptors(Class<?> sourceClass) {
		List<PropertyDescriptor> propertys = new ArrayList<>();
		try {
			List<PropertyDescriptor> propertyTmp = Arrays
					.asList(Introspector.getBeanInfo(sourceClass).getPropertyDescriptors());
			for (PropertyDescriptor property : propertyTmp) {
				String name = property.getName();
				if ("class".equals(name)) {
					continue;
				}
				propertys.add(property);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return propertys;
	}

	/**
	 * @param string
	 * @return
	 */
	public Invoker<T> getFieldReader(String fieldName) {
		int hashCodeFlag = ("R" + fieldName).hashCode();
		Invoker<T> invoker = this.publicMethodInvoke.get(hashCodeFlag);
		if (invoker == null) {
			throw new RuntimeException(String.format(
					"can not find any write methoh for field : %s  , Maybe the field has been filtered or the field was not exists in class:%s",
					fieldName, this.hostClass.getName()));
		}
		return invoker;
	}

	public Invoker<T> getFieldWriter(String fieldName) {
		int hashCodeFlag = ("W" + fieldName).hashCode();
		Invoker<T> invoker = this.publicMethodInvoke.get(hashCodeFlag);
		if (invoker == null) {
			throw new RuntimeException(String.format(
					"can not find any read methoh for field : %s  , Maybe the field has been filtered or the field was not exists in class:%s",
					fieldName, this.hostClass.getName()));
		}
		return invoker;
	}

	public Invoker<T> getMethodInvoker(String methodName, Class<?>[] paramTypes) {
		return this.publicMethodInvoke.get(SpecialNameRegistry.getInstance().getMethodIdAndAllocate(this.hostClassName,
				methodName, SpecialNameRegistry.argumentTypesToString(paramTypes)));
	}

	/**
	 * 对原有的invoker进行替换
	 * 
	 * @param flag
	 * @param invoker
	 */
	public void replaceInvoker(int flag, Invoker<T> invoker) {
		this.publicMethodInvoke.put(flag, invoker);
	}

	public java.lang.reflect.Type getFieldType(String fieldName) {
		return fieldTypeMapper.get(fieldName);
	}

}
