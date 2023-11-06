package com.zero.helper;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.zero.helper.beanInvoker.BeanInvokeUtils;
import com.zero.helper.beanInvoker.BeanMethodInvoke;
import com.zero.helper.beanInvoker.Invoker;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

/**
 * 
 * @title GenericsUtils
 * @desc 一些通用的工具方法
 * @author Rezar
 * @date 2015年9月13日
 * @version 1.0
 */
@Slf4j
public class GU {

	/**
	 * 判断某个对象是否为空或者如果是某些特殊对象的话，判断这些特殊对象的长度属性，是否为Empty
	 * 
	 * @param obj
	 * @return
	 */
	public static boolean notNullAndEmpty(Object obj) {
		if (obj == null) {
			return false;
		} else if (obj instanceof String) {
			return ((String) obj).length() == 0 ? false : true;
		} else if (obj instanceof Collection<?>) {
			return ((Collection<?>) obj).size() == 0 ? false : true;
		} else if (obj instanceof Map<?, ?>) {
			return ((Map<?, ?>) obj).size() == 0 ? false : true;
		} else if (obj instanceof StringBuilder) {
			return ((StringBuilder) obj).length() == 0 ? false : true;
		} else if (obj instanceof StringBuffer) {
			return ((StringBuffer) obj).length() == 0 ? false : true;
		} else {
			Class<?> cla = obj.getClass();
			if (cla.isArray()) {
				return Array.getLength(obj) == 0 ? false : true;
			}
		}
		return true;
	}

	public static boolean isNullOrEmpty(Object obj) {
		return !notNullAndEmpty(obj);
	}

	/**
	 * 判断一个对象中的某个属性是否为普通的属性，且判断该属性是否为默认值
	 * 
	 * @param field
	 * @param qm
	 * @return
	 * @throws IllegalArgumentException
	 * @throws IllegalAccessException
	 */
	public static boolean isNotNull(Field field, Object qm) throws IllegalArgumentException, IllegalAccessException {
		boolean notNull = true;
		Class<?> type = field.getType();
		if (type == int.class) {
			notNull = ((Integer) field.get(qm) == 0 ? false : true);
		} else if (type == double.class) {
			notNull = ((Double) field.get(qm) == 0.0 ? false : true);
		} else if (type == float.class) {
			notNull = ((Float) field.get(qm) == 0.0 ? false : true);
		} else if (type == boolean.class) {
			notNull = ((Boolean) field.get(qm) == false ? false : true);
		} else if (type == char.class) {
			notNull = ((Character) field.get(qm) == '\u0000' ? false : true);
		} else if (type == byte.class) {
			notNull = ((Byte) field.get(qm) == 0 ? false : true);
		} else if (type == short.class) {
			notNull = ((Short) field.get(qm) == 0 ? false : true);
		}
		return notNull;
	}

	public static boolean isNullOrDefaultValue(Object valueObj, String fieldName, Object... defaultValue)
			throws IllegalArgumentException, IllegalAccessException {
		boolean isNullOrDefaultValue = true;
		Object defaultFieldValue = (isNullOrEmpty(defaultValue) ? null : defaultValue[0]);
		Invoker<Object> fieldReaderInvoker = getFieldReaderInvoker(fieldName, null, valueObj);
		Object fieldValue = fieldReaderInvoker.invoke(valueObj);
		if (fieldValue == null) {
			return true;
		}
		Class<?> fieldType = fieldValue.getClass();
		if (defaultFieldValue != null && (fieldValue.equals(defaultFieldValue))) {
			isNullOrDefaultValue = true;
		} else if (fieldType == int.class) {
			isNullOrDefaultValue = (((Integer) fieldValue).intValue() == 0 ? true : false);
		} else if (fieldType == double.class) {
			isNullOrDefaultValue = (((Double) fieldValue).doubleValue() == 0.0 ? true : false);
		} else if (fieldType == float.class) {
			isNullOrDefaultValue = (((Float) fieldValue).floatValue() == 0.0 ? true : false);
		} else if (fieldType == boolean.class) {
			isNullOrDefaultValue = (((Boolean) fieldValue).booleanValue() == false ? true : false);
		} else if (fieldType == char.class) {
			isNullOrDefaultValue = (((Character) fieldValue).charValue() == '\u0000' ? true : false);
		} else if (fieldType == byte.class) {
			isNullOrDefaultValue = (((Byte) fieldValue).byteValue() == 0 ? true : false);
		} else if (fieldType == short.class) {
			isNullOrDefaultValue = (((Short) fieldValue).shortValue() == 0 ? true : false);
		}
		return isNullOrDefaultValue;
	}

	public static boolean notEqualsIn(String value, boolean ignoreCase, String... strs) {
		boolean isIn = false;
		for (int i = 0; i < strs.length; i++) {
			if (ignoreCase) {
				isIn = strs[i].equals(value);
			} else {
				isIn = strs[i].equalsIgnoreCase(value);
			}
		}
		return isIn;
	}

	/**
	 * 从一个字符串中找到某个字符串出现的位置，从指定开始的位置开始查找
	 * 
	 * @param str
	 * @return
	 */
	public static int indexOfIgnoreCase(String str, int fromIndex) {
		str = str.toLowerCase();
		return str.indexOf(str, fromIndex);

	}

	public static StringBuilder deleteLastChar(StringBuilder sb) {
		if (sb != null && sb.length() > 0) {
			return sb.deleteCharAt(sb.length() - 1);
		} else {
			return sb;
		}
	}

	public static String deleteLastCharToString(StringBuilder sb) {
		return deleteLastChar(sb).toString();
	}

	public static <T extends Collection<K>, K> List<K> toList(T cols) {
		return new ArrayList<K>(cols);
	}

	/**
	 * 根据对象集合,获取对象中某个字段的属性值的集合<br/>
	 * 
	 * 从一个集合里面取出某个字段的属性作为key,返回所有对象里面这个属性的值的集合
	 * 
	 * @param cols
	 * @param fieldName
	 * @return
	 */
	@SuppressWarnings("unchecked")
	public static <K, V> List<K> toFieldList(Collection<V> cols, String fieldName) {
		if (isNullOrEmpty(cols) || isNullOrEmpty(fieldName)) {
			return emptyList();
		}
		Iterator<? extends V> iter = cols.iterator();
		List<K> retList = new ArrayList<>(cols.size());
		Invoker<V> fieldReader = null;
		while (iter.hasNext()) {
			V next = iter.next();
			if (fieldReader == null) {
				BeanMethodInvoke<V> findBeanMethodInovker = (BeanMethodInvoke<V>) BeanInvokeUtils
						.findBeanMethodInovker(next.getClass());
				fieldReader = findBeanMethodInovker.getFieldReader(fieldName);
			}
			Object invoke = fieldReader.invoke(next);
			retList.add((K) invoke);
		}
		return retList;
	}

	/**
	 * 根据List ---> keyValue<br/>
	 * 从一个集合里面取出某个字段的属性作为key,如果指定了两个以上的keyName,则第一个始终为key值的属性名.第二个为value值的属性名.
	 * 
	 * @param cols
	 * @return
	 */
	@SuppressWarnings("unchecked")
	public static <T extends Collection<V>, V, K, R> Map<K, R> toFieldMap(T cols, String... keyNames) {
		if (isNullOrEmpty(cols) || isNullOrEmpty(keyNames)) {
			log.debug("cols is null:{} or field is null :{} ", isNullOrEmpty(cols), isNullOrEmpty(keyNames));
			return emptyMap();
		}
		String keyOfKey = keyNames[0];
		String keyOfValue = null;
		if (keyNames.length >= 2) {
			keyOfValue = keyNames[1];
		}
		Iterator<V> iter = cols.iterator();
		Map<K, R> retMap = new HashMap<>(cols.size());
		Invoker<V> fieldReaderForKey = null;
		Invoker<V> fieldReaderForValue = null;
		while (iter.hasNext()) {
			V next = iter.next();
			fieldReaderForKey = getFieldReaderInvoker(keyOfKey, fieldReaderForKey, next);
			Object key = fieldReaderForKey.invoke(next);
			Object value = null;
			if (keyOfValue != null) {
				value = getFieldReaderInvoker(keyOfValue, fieldReaderForValue, next).invoke(next);
			} else {
				value = next;
			}
			retMap.put((K) key, (R) value);
		}
		return retMap;
	}

	/**
	 * 
	 * 从一个集合中根据主键取出所有相同key的value,放在List中,返回Map<key,List<Value>>
	 * 
	 * @param cols
	 * @return
	 */
	@SuppressWarnings("unchecked")
	public static <T extends Collection<V>, V, K, R> Map<K, List<R>> toFieldListMap(T cols, String... keyNames) {
		if (isNullOrEmpty(cols) || isNullOrEmpty(keyNames)) {
			log.debug("cols is null:{} or field is null :{} ", isNullOrEmpty(cols), isNullOrEmpty(keyNames));
			return emptyMap();
		}
		String keyOfKey = keyNames[0];
		String keyOfValue = null;
		if (keyNames.length >= 2) {
			keyOfValue = keyNames[1];
		}
		Iterator<V> iter = cols.iterator();
		Map<K, List<R>> retMap = new HashMap<>(cols.size());
		Invoker<V> fieldReaderForKey = null;
		Invoker<V> fieldReaderForValue = null;
		while (iter.hasNext()) {
			V next = iter.next();
			fieldReaderForKey = getFieldReaderInvoker(keyOfKey, fieldReaderForKey, next);
			Object key = fieldReaderForKey.invoke(next);
			Object value = null;
			if (keyOfValue != null) {
				value = getFieldReaderInvoker(keyOfValue, fieldReaderForValue, next).invoke(next);
			} else {
				value = next;
			}
			addListIfNotExists(retMap, (K) key, (R) value);
		}
		return retMap;
	}

	/**
	 * @param keyOfKey
	 * @param fieldReader
	 * @param next
	 * @return
	 */
	@SuppressWarnings("unchecked")
	public static <V> Invoker<V> getFieldReaderInvoker(String keyOfKey, Invoker<V> fieldReader, V next) {
		if (fieldReader == null) {
			BeanMethodInvoke<V> findBeanMethodInovker = (BeanMethodInvoke<V>) BeanInvokeUtils
					.findBeanMethodInovker(next.getClass());
			fieldReader = findBeanMethodInovker.getFieldReader(keyOfKey);
		}
		return fieldReader;
	}

	/**
	 * 
	 * replace with @toFieldList
	 * 
	 * @param cols
	 * @param field
	 * @return
	 */
	@Deprecated
	public static <K, V> List<K> toKeyList(Collection<? extends V> cols, String field) {
		return toFieldList(cols, field);
	}

	public static void setAccessable(String fieldName, Class<?> beanClass) {
		try {
			Field field = beanClass.getDeclaredField(fieldName);
			if (!field.isAccessible()) {
				field.setAccessible(true);
			}
		} catch (Exception e) {
			log.debug("can not set accessible for field : {} in class : {} ", fieldName, beanClass);
		}
	}

	/**
	 * 添加这个方法的原因:<br/>
	 * 因为之前都是调用 , Collections.emptyList(); ,<br/>
	 * 这个方法会返回一个EmptyList对象，该对象继承AbstractList父类 其add方法未实现.<br/>
	 * 所以在使用底层方法返回的Collections.emptyList() 对象再添加新数据的时候，会报
	 * java.lang.UnsupportedOperationException
	 * 
	 * @return
	 */
	public static <T> List<T> emptyList() {
		return new ArrayList<>();
	}

	/**
	 * @see GenericsUtils.emptyList()
	 * @return
	 */
	public static <K, V> Map<K, V> emptyMap() {
		return new HashMap<>();
	}

	/**
	 * @see GenericsUtils.emptyList()
	 * @return
	 */
	public static <T> Set<T> emptySet() {
		return new HashSet<>();
	}

	/**
	 * @param key
	 * @param listValue
	 */
	public static <K, V> void addListIfNotExists(Map<K, List<V>> map, K key, V listValue) {
		List<V> list = map.get(key);
		if (isNullOrEmpty(list)) {
			list = new ArrayList<>();
			map.put(key, list);
		}
		list.add(listValue);
	}

	private static final String COMMON_SPLIT_RETEX = "[,|:&#]+";

	public static <T> Set<T> stringToNumber(String numStr, Class<T> numType) {
		return stringToNumber(numStr, COMMON_SPLIT_RETEX, numType);
	}

	@SuppressWarnings("unchecked")
	public static <T> Set<T> stringToNumber(String numStr, String splitRegex, Class<T> numType) {
		if (GU.isNullOrEmpty(numStr)) {
			return emptySet();
		}

		String[] allValues = numStr.split(splitRegex);
		Set<T> retSet = Sets.newHashSet();
		for (String value : allValues) {
			Object numValue = getNumValueFromStr(numType, value);
			retSet.add((T) numValue);
		}
		return retSet;
	}

	@SuppressWarnings("unchecked")
	public static <T> List<T> stringToNumberList(String numStr, String splitRegex, Class<T> numType) {
		if (GU.isNullOrEmpty(numStr)) {
			return emptyList();
		}

		String[] allValues = numStr.split(splitRegex);
		List<T> retList = new ArrayList<>();
		for (String value : allValues) {
			Object numValue = getNumValueFromStr(numType, value);
			retList.add((T) numValue);
		}
		return retList;
	}

	/**
	 * @param numType
	 * @param value
	 * @return
	 */
	@SuppressWarnings("unchecked")
	public static <T> T getNumValueFromStr(Class<T> numType, String value) {
		Object numValue = null;
		if (numType == Integer.class || numType == int.class) {
			numValue = Integer.parseInt(value);
		} else if (numType == Float.class || numType == float.class) {
			numValue = Float.parseFloat(value);
		} else if (numType == Short.class || numType == short.class) {
			numValue = Short.parseShort(value);
		} else if (numType == Long.class || numType == long.class) {
			numValue = Long.parseLong(value);
		} else if (numType == Double.class || numType == double.class) {
			numValue = Double.parseDouble(value);
		} else if (numType == Byte.class || numType == byte.class) {
			numValue = Byte.parseByte(value);
		} else if (numType == Character.class || numType == char.class) {
			numValue = new Character(value.charAt(0));
		}
		return (T) numValue;
	}

	/**
	 * 打印异常,logger 对象在error输出方法里面输出常规信息的时候,异常栈信息会缺失,需要分开打印
	 * 
	 * @param logger
	 *            :异常对象
	 * @param ex
	 *            :异常对象
	 * @param format
	 *            :输出格式,同 can not find any orgInfo with orgId:{} ,使用{}占位
	 * @param infos
	 *            :需要输出的占位信息
	 */
	public static void logErrorAndInfo(Logger logger, Exception ex, String format, Object... infos) {
		if (GU.notNullAndEmpty(format)) {
			format = format.replace("{}", "%s");
			logger.info("[Pring Error Info ] {} ", String.format(format, infos));
		}
		logger.error("[exception] {} ", ex);// the really excepiton not in
											// GenericsUtils ,please find out
											// the
											// exception
											// position from StackTrace
	}

	public static void logErrorAndInfo(Class<?> clazz, Exception ex, String format, Object... infos) {
		Logger log = LoggerFactory.getLogger(clazz);
		logErrorAndInfo(log, ex, format, infos);
	}

	/**
	 * @return
	 */
	public static String formatOutput(String format, Object... params) {
		if (GU.isNullOrEmpty(params) || GU.isNullOrEmpty(format)) {
			return format;
		}
		if (format.contains("{}")) {
			format = format.replace("{}", "%s");
		}
		return String.format(format, params);
	}

	/**
	 * 对数据进行分组排序操作
	 * 
	 * @param list
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static Map<Object, Object> groupSortForRetMap(Collection<Object> list, List<Comparator> comparators) {
		if (GU.isNullOrEmpty(list) || GU.isNullOrEmpty(comparators)) {
			throw new IllegalArgumentException("illegal arguments");
		}
		Map<Object, Object> retMap = Maps.newLinkedHashMap();
		Map<Object, List<Object>> tempMap = Maps.newHashMap();
		Comparator groupSortComparator = comparators.remove(0);
		Set<Object> keys = Sets.newHashSet();
		boolean isSortWithMulit = false;
		if (groupSortComparator instanceof MulitGroupSortComparator) {
			isSortWithMulit = true;
		}
		for (Object obj : list) {
			Object pid = null;
			if (groupSortComparator instanceof comparatorSuper) {
				pid = ((comparatorSuper) groupSortComparator).getPid(obj);
			}
			keys.add(pid);
			if (isSortWithMulit) {
				pid = ((((MulitGroupSortComparator) groupSortComparator).getPid(obj)).getPid());
			}
			addListIfNotExists(tempMap, pid, obj);
		}
		ArrayList<Object> keyReplace = Lists.newArrayList(keys);
		Collections.sort(keyReplace, groupSortComparator);
		for (Object key : keyReplace) {
			if (isSortWithMulit) {
				key = ((MulitGroupSorter) key).getPid();
			}
			List<Object> remove = tempMap.remove(key);
			// 默认相同pid的数据只对第一个取值
			if (GU.isNullOrEmpty(remove)) {
				continue;
			}
			if (GU.isNullOrEmpty(comparators)) {
				retMap.put(key, remove);
			} else {
				List<Comparator> comparatorList = Lists.newArrayList(comparators);
				retMap.put(key, groupSortForRetMap(remove, comparatorList));
			}
		}
		return retMap;
	}

	@SuppressWarnings({ "rawtypes" })
	public static <T> List<T> groupSort(Collection<Object> list, List<Comparator> comparators) {
		Map<Object, Object> groupSortForRetMap = groupSortForRetMap(list, comparators);
		System.out.println("groupSortForRetMap is : " + groupSortForRetMap);
		List<T> retList = findFromMap(groupSortForRetMap);
		return retList;
	}

	/**
	 * @param groupSortForRetMap
	 * @return
	 */
	@SuppressWarnings("unchecked")
	private static <T> List<T> findFromMap(Map<Object, Object> groupSortForRetMap) {
		List<T> retList = Lists.newArrayList();
		for (Map.Entry<Object, Object> entry : groupSortForRetMap.entrySet()) {
			Object value = entry.getValue();
			if (value instanceof Map<?, ?>) {
				retList.addAll(GU.<T>findFromMap((Map<Object, Object>) value));
			} else if (value instanceof Collection<?>) {
				for (Object val : (Collection<?>) value) {
					retList.add((T) val);
				}
			}
		}
		return retList;
	}

	public static interface comparatorSuper<F, T> {
		public F getPid(T obj);
	}

	public static interface GroupSortComparator<F, T> extends Comparator<F>, comparatorSuper<F, T> {

	}

	public static interface MulitGroupSortComparator<T>
			extends Comparator<MulitGroupSorter<T>>, comparatorSuper<MulitGroupSorter<T>, T> {

		public MulitGroupSorter<T> getPid(T obj);

	}

	@Data
	public static abstract class MulitGroupSorter<T> {

		private T obj;

		public abstract Object getPid();

		/**
		 * @param obj2
		 */
		public MulitGroupSorter(T obj2) {
			obj = obj2;
		}

	}

	/**
	 * 将一个数组数据按照每个分组里面数据量最相近的方式划分为targetNum个分组
	 * 
	 * @param values
	 * @param targetNum
	 * @return
	 */
	public static <T> List<List<T>> divideToSomeCountArray(List<T> values, int targetNum) {

		return null;
	}

	/**
	 * 未完全均分,待上面的方法实现
	 * 
	 * @param lists
	 * @param limit
	 * @return
	 */
	public static <T> List<List<T>> splitList(List<T> lists, int limit) {
		int size = lists.size();
		List<List<T>> list = new ArrayList<List<T>>();
		if (limit > size) {
			list.add(lists);
			return list;
		}
		int result = 0;
		for (int i = 0; i < size; i = i + limit) {
			result = i + limit;
			if (result > size) {
				result = size;
			}
			list.add(lists.subList(i, result));
		}
		return list;
	}
	
	public static <T> Stream<List<T>> split(
			List<T> origin, 
			Integer parallelNum) {
		int size = origin.size();
		return 
				Stream.iterate(
						0, 
						n -> n + 1)
				.limit(Integer.valueOf((int) Math.ceil(size / parallelNum.doubleValue())))
				.map(roundIndex -> {
					return origin.stream()
							.skip(roundIndex * parallelNum)
							.limit(parallelNum)
							.collect(Collectors.toList());
				});
	}

	/**
	 * 
	 * @return
	 */
	public static Class<?> findGenericsTypeFromSuperClass(Class<?> superClass) {
		ParameterizedType superType = (ParameterizedType) superClass.getGenericSuperclass();
		return (Class<?>) superType.getActualTypeArguments()[0];
	}

	private static final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

	/**
	 * @param valueStr
	 * @return
	 */
	public static Date simpleFormatDateStr(String valueStr) {
		if (GU.isNullOrEmpty(valueStr)) {
			return null;
		}
		try {
			return sdf.parse(valueStr);
		} catch (Exception e) {
			log.warn("can not parserValueStr:{} and error is:{} ", valueStr, e);
			return new Date();
		}
	}

	/**
	 * @param fieldValueMap
	 * @return
	 */
	public static <K, V> Map<K, V> copyMap(Map<K, V> fieldValueMap) {
		if (GU.isNullOrEmpty(fieldValueMap)) {
			return GU.emptyMap();
		}
		Map<K, V> newRetMap = new HashMap<>(fieldValueMap.size());
		for (K key : fieldValueMap.keySet()) {
			newRetMap.put(key, fieldValueMap.get(key));
		}
		return newRetMap;
	}

	/**
	 * @param propFile
	 * @return
	 * 
	 */
	// public static Properties findPropWithNoError(String propFile) {
	// Properties fillProperties = null;
	// try {
	// fillProperties = PropertiesReader.fillProperties(propFile);
	// } catch (Exception e) {
	// log.info("can not find prop with fileName:{}", propFile);
	// }
	// return fillProperties;
	// }

	@SuppressWarnings("unchecked")
	public static <T> T getNullWithoutError(Map<String, Object> options, String key) {
		if (GU.isNullOrEmpty(options) || GU.isNullOrEmpty(key)) {
			return null;
		}
		Object object = options.get(key);
		if (object == null) {
			return null;
		}
		try {
			return (T) object;
		} catch (Exception e) {
			log.info("error while cast object:{} with class:{} ", object, object.getClass().getName());
			return null;
		}
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static <T> FillInfoContext<T> createInfoFillContext(List<T> sourceObjes,
			FillInfoCallback... fillInfoCallbackes) {
		if (GU.notNullAndEmpty(fillInfoCallbackes)) {
			return new FillInfoContext<>(sourceObjes)
					.addInfoFiller(Arrays.<FillInfoCallback<T>>asList(fillInfoCallbackes));
		} else {
			return new FillInfoContext<>(sourceObjes);
		}
	}

	public static interface FillInfoCallback<T> {
		public void fillInfo(Collection<T> objes, FillInfoContext<T> context);
	}

	public static final class FillInfoContext<T> {

		private Collection<T> sourceObjs = null;
		private Map<String, Object> context = new HashMap<>();
		private List<FillInfoCallback<T>> infoFillers = new ArrayList<>();

		public FillInfoContext() {
		}

		/**
		 * @param fillInfoCallbackes
		 * @return
		 */
		public FillInfoContext<T> addInfoFiller(Collection<FillInfoCallback<T>> infoFilles) {
			this.infoFillers.addAll(infoFillers);
			return this;
		}

		public FillInfoContext(Collection<T> sourceObjes) {
			this.sourceObjs = sourceObjes;
		}

		public void addInfo(String infoKey, Object info) {
			context.put(infoKey, info);
		}

		public void addInfoFiller(String fieldKey, FillInfoCallback<T> infoFiller) {
			infoFillers.add(infoFiller);
		}

		public void addSourceObj(T sourceObj) {
			this.sourceObjs.add(sourceObj);
		}

		@SuppressWarnings("unchecked")
		public <E> E getInfoSource(String infoKey) {
			Object infoObj = context.get(infoKey);
			if (infoObj == null) {
				return null;
			}
			return (E) infoObj;
		}

		public void fillInfo() {
			if (this.sourceObjs != null && GU.notNullAndEmpty(this.infoFillers)) {
				for (FillInfoCallback<T> infoFiller : this.infoFillers) {
					infoFiller.fillInfo(sourceObjs, this);
				}
			}
		}

	}

}
