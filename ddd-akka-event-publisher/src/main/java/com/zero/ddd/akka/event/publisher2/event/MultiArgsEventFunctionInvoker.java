package com.zero.ddd.akka.event.publisher2.event;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.commons.lang3.tuple.Pair;

import com.zero.helper.JacksonUtil;
import com.zero.helper.beanInvoker.Invoker;

import lombok.extern.slf4j.Slf4j;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2023-03-12 11:28:59
 * @Desc 些年若许,不负芳华.
 *
 */
@Slf4j
public class MultiArgsEventFunctionInvoker implements EventFunctionInvoker {
	
	private Object target;
	private Invoker<Object> methodInvoker;
	private Map<String, Class<?>> paramMap;
	private Map<String, Integer> eventTypeIndexMap;
	
	public MultiArgsEventFunctionInvoker(
			Object target, 
			Class<?>[] methodPar,
			Invoker<Object> methodInvoker) {
		this.target = target;
		this.methodInvoker = methodInvoker;
		this.paramMap = 
				Arrays.asList(
						methodPar)
				.stream()
				.collect(
						Collectors.toMap(
								clazz -> clazz.getSimpleName(), 
								clazz -> clazz));
		this.eventTypeIndexMap = 
				IntStream.range(
						0, 
						methodPar.length)
				.mapToObj(index -> {
					String argsType = 
							methodPar[index].getSimpleName();
					return Pair.of(argsType, index);
				})
				.collect(Collectors.toMap(Pair::getKey, Pair::getRight));
		log.info(
				"paramMap:{}-eventTypeIndexMap:{}", 
				JacksonUtil.obj2Str(paramMap), 
				JacksonUtil.obj2Str(eventTypeIndexMap));
	}
	
	@Override
	public final void execute(
			String eventType, 
			String eventBody) throws Exception {
		if (!this.eventTypeIndexMap.containsKey(eventType)) {
			log.warn(
					"监听器类:{}下的监听方法包含不识别的事件类型:{} 事件体:{}", 
					this.target.getClass().getSimpleName(), 
					eventType, 
					eventBody);
			return;
		}
		JacksonUtil.str2ObjOpt(
				eventBody, 
				this.argsType(eventType))
		.ifPresent(obj -> {
			Object[] parseArgs = 
					this.parseArgs(eventType, obj);
			try {
				this.methodInvoker.invoke(
						this.target, 
						parseArgs);
			} finally {
				Arrays.fill(parseArgs, null);
			}
		});
	}

	protected Object[] parseArgs(
			String eventType, 
			Object eventBody) {
		Object[] args = null;
		// 以最大消费事件参数数量为最终对象数组大小
		if ((args = localArgs.get()) == null
				|| args.length < this.eventTypeIndexMap.size()) {
			localArgs.set(
					args = new Object[this.eventTypeIndexMap.size()]);
		}
		args[this.eventTypeIndexMap.get(eventType)] = eventBody;
		return args;
	}
	
	private static final ThreadLocal<Object[]> localArgs = new ThreadLocal<Object[]>();

	private Class<?> argsType(
			String eventType) {
		return this.paramMap.get(eventType);
	}

}