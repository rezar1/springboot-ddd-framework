
/**
 * Baijiahulian.com Inc. Copyright (c) 2014-2016 All Rights Reserved.
 */

package com.zero.helper.beanInvoker;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import com.zero.helper.GU;

/**
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time Sep 24, 2016
 * @Desc this guy is too lazy, nothing left.
 */
public class SpecialNameRegistry {

	private AtomicInteger incr = new AtomicInteger(0);

	private ConcurrentHashMap<String, Integer> nameIds = new ConcurrentHashMap<>();

	private final String CLASS_NAME_PREFIX_FORMAT = "class:%s";

	private final String METHOD_NAME_PREFIX_FORMAT = "method:%s:%s:%s";

	private static SpecialNameRegistry instance = new SpecialNameRegistry();

	private SpecialNameRegistry() {
	}

	public static SpecialNameRegistry getInstance() {
		return instance;
	}

	/**
	 * 输出类型信息
	 * 
	 * @param argTypes
	 * @return
	 */
	public static String[] argumentTypesToString(Class<?>[] argTypes) {
		String[] argus = new String[argTypes.length];
		if (argTypes != null) {
			for (int i = 0; i < argTypes.length; i++) {
				Class<?> c = argTypes[i];
				argus[i] = c.getName();
			}
		}
		return argus;
	}

	/**
	 * 获取className
	 * 
	 * @param num
	 * @return
	 */
	public String getClassName(Integer num) {
		return this.findNameWithNum(num).get(0);
	}

	/**
	 * 获取方法
	 * 
	 * @param num
	 * @return
	 */
	public List<String> getMethodName(Integer num) {
		return this.findNameWithNum(num);
	}

	private List<String> findNameWithNum(Integer num) {
		for (Map.Entry<String, Integer> entry : this.nameIds.entrySet()) {
			if (entry.getValue().equals(num)) {
				return decodeClassName(entry.getKey());
			}
		}
		return null;
	}

	/**
	 * @param integer
	 * @return
	 */
	private List<String> decodeClassName(String classNameWithPrefix) {
		String[] ret = classNameWithPrefix.split(":");
		List<String> infos = Arrays.asList(ret);
		return infos.subList(0, infos.size());
	}

	public Integer getClassId(String className) {
		String formatClassName = formatClassName(className);
		return this.getIdForName(formatClassName);
	}

	public Integer getClassIdAndAllocate(String className) {
		return this.getIdForNameAndAllocate(formatClassName(className), true);
	}

	public Integer getMethodId(String className, String methodName, String... paramTypes) {
		return this.getIdForNameAndAllocate(formatMethodName(className, methodName, paramTypes), false);
	}

	public Integer getMethodIdAndAllocate(String className, String methodName, String... paramTypes) {
		String methodMark = formatMethodName(className, methodName, paramTypes);
		return this.getIdForNameAndAllocate(methodMark, true);
	}

	private Integer getIdForName(String name) {
		return this.getIdForNameAndAllocate(name, false);
	}

	private Integer getIdForNameAndAllocate(String name, boolean allocate) {
		if (GU.isNullOrEmpty(name)) {
			return null;
		}
		Integer num = this.nameIds.get(name);
		if (num == null && allocate) {
			synchronized (instance) {
				num = this.incr.incrementAndGet();
				this.nameIds.putIfAbsent(name, num);
			}
		}
		return num;
	}

	private String formatClassName(String className) {
		if (GU.isNullOrEmpty(className)) {
			return null;
		}
		return String.format(CLASS_NAME_PREFIX_FORMAT, className);
	}

	private String formatMethodName(String className, String methodName, String[] paramTypes) {
		if (GU.isNullOrEmpty(className) || GU.isNullOrEmpty(methodName)) {
			return null;
		}
		StringBuilder typeDescStr = new StringBuilder();
		if (GU.notNullAndEmpty(paramTypes)) {
			for (String paramType : paramTypes) {
				typeDescStr.append(paramType).append(",");
			}
		}
		return String.format(METHOD_NAME_PREFIX_FORMAT, className, methodName,
				GU.deleteLastChar(typeDescStr));
	}

}
