package com.zero.ddd.akka.event.publisher2.beanProcessor;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.commons.lang3.tuple.Pair;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.MethodIntrospector;
import org.springframework.core.annotation.AnnotationUtils;

import com.google.common.collect.Sets;
import com.zero.ddd.akka.event.publisher2.event.CustomizedEventSynchronizer;
import com.zero.ddd.akka.event.publisher2.event.EventFunctionInvoker;
import com.zero.ddd.akka.event.publisher2.event.MultiArgsEventFunctionInvoker;
import com.zero.ddd.akka.event.publisher2.event.annotations.BatchConsume;
import com.zero.ddd.akka.event.publisher2.event.annotations.EventAppServerName;
import com.zero.ddd.akka.event.publisher2.event.annotations.EventSynchronizer;
import com.zero.ddd.akka.event.publisher2.event.annotations.ShardingKeyExpression;
import com.zero.helper.GU;
import com.zero.helper.beanInvoker.BeanInvokeUtils;
import com.zero.helper.beanInvoker.BeanMethodInvoke;
import com.zero.helper.beanInvoker.Invoker;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2023-04-21 07:22:36
 * @Desc 些年若许,不负芳华.
 * 
 */
@RequiredArgsConstructor
public class EventSynchronizerBeanProcessor implements BeanPostProcessor {
	
	private final Set<Class<?>> nonAnnotatedClasses = Collections.newSetFromMap(new ConcurrentHashMap<>(64));
	
	private final boolean enable;
	private final EventSynchronizerRegister register;

	@Override
	public Object postProcessAfterInitialization(
			Object bean, 
			String beanName) throws BeansException {
		if (!enable) {
			return bean;
		}
		if (!this.nonAnnotatedClasses.contains(bean.getClass())) {
			Class<?> targetClass = AopUtils.getTargetClass(bean);
			String baseEventAppName = 
					Optional.ofNullable(
							targetClass.getAnnotation(
									EventAppServerName.class))
					.map(ann -> ann.value())
					.orElse(null);
			Map<Method, Set<EventSynchronizer>> annotatedMethods = 
					MethodIntrospector.selectMethods(
							targetClass,
							(MethodIntrospector.MetadataLookup<Set<EventSynchronizer>>) method -> {
								EventSynchronizer jobAnnotation = 
										this.findDistributedJobAnnotations(method);
								return (jobAnnotation == null ? 
										null : Sets.newHashSet(jobAnnotation));
							});
			if (annotatedMethods.isEmpty()) {
				this.nonAnnotatedClasses.add(bean.getClass());
			} else {
				for (Map.Entry<Method, Set<EventSynchronizer>> entry : annotatedMethods.entrySet()) {
					Method method = entry.getKey();
					for (EventSynchronizer eventSyn : entry.getValue()) {
						processEventSynchronizer(
								baseEventAppName, 
								eventSyn,
								targetClass, 
								method,
								bean, 
								beanName);
					}
				}
			}
		}
		return bean;
	}

	@SuppressWarnings("unchecked")
	private void processEventSynchronizer(
			String baseEventAppName, 
			EventSynchronizer eventSynchronizer, 
			Class<?> targetClass, 
			Method method, 
			Object bean,
			String beanName) {
		String appServerName = 
				GU.isNullOrEmpty(eventSynchronizer.appName()) ? 
						baseEventAppName : eventSynchronizer.appName();
		if (GU.isNullOrEmpty(appServerName)) {
			throw new IllegalArgumentException(
					String.format(
							"类:[%s] 方法:[%s]没有注明事件所属服务名称, 请使用[@EventAppServerName]注解标注类或者在@EventSynchronizer中填写appName", 
							beanName,
							method.getName()));
		}
		BeanMethodInvoke<Object> beanMethodInovker = 
				(BeanMethodInvoke<Object>) BeanInvokeUtils.findBeanMethodInovker(
						targetClass, 
						null, 
						methodBak -> !methodBak.isAnnotationPresent(EventSynchronizer.class));
		String synchronizerId = 
				GU.isNullOrEmpty(eventSynchronizer.synchronizerId()) ? 
						method.getName() : eventSynchronizer.synchronizerId();
		Class<?>[] parameterTypes = 
				method.getParameterTypes();
		Invoker<Object> methodInvoker = 
				beanMethodInovker.getMethodInvoker(
						method.getName(),
						parameterTypes);
		EventFunctionInvoker functionInvoker = 
				new MultiArgsEventFunctionInvoker(
						bean, 
						parameterTypes,
						methodInvoker);
		Map<String, EventTypeExpression> expressionMap = 
				this.parseTypeShardingKeyExpressionMap(
						method.getAnnotation(
								ShardingKeyExpression.class), 
						parameterTypes,
						method.getParameterAnnotations());
		this.register.registe(
				CustomizedEventSynchronizer.builder()
				.eventSynchronizer(
						com.zero.ddd.akka.event.publisher2.event.EventSynchronizer.builder()
						.appName(appServerName)
						.synchornizerId(synchronizerId)
						.partition(eventSynchronizer.partition())
						.eventBatchConsumeConfig(
								this.buildBatchEventConsumeConfig(
										eventSynchronizer.batchConsume()))
						.typeShardingHashValExpression(expressionMap)
						.awareEventTypes(
								Arrays.asList(
										parameterTypes)
								.stream()
								.map(clazz -> clazz.getSimpleName())
								.collect(Collectors.toSet()))
						.build())
				.concurrency(eventSynchronizer.clientConcurrency())
				.eventFunctionInvoker(functionInvoker)
				.build());
	}

	private EventBatchConfig buildBatchEventConsumeConfig(
			BatchConsume batchConsume) {
		return 
				Optional.ofNullable(batchConsume)
				.filter(BatchConsume::useing)
				.map(configAnnt -> {
					return 
							new EventBatchConfig(
									Duration.ofMillis(
											configAnnt.timeWindowsUnit().toMillis(
													configAnnt.timeWindows())),
									configAnnt.batchSize());
				})
				.orElse(null);
	}

	private Map<String, EventTypeExpression> parseTypeShardingKeyExpressionMap(
			ShardingKeyExpression commonShardingKeyExpressionAnnot, 
			Class<?>[] parameterTypes, 
			Annotation[][] annotations) {
		EventTypeExpression commonShardingKeyExpression = 
				Optional.ofNullable(
						commonShardingKeyExpressionAnnot)
				.map(annt -> {
					return new EventTypeExpression(
							annt.el(), 
							annt.filterEl());
				})
				.orElse(null);
		Map<String, EventTypeExpression> expressionMap = 
				IntStream.range(
						0, parameterTypes.length)
				.mapToObj(index -> {
					Class<?> parType = parameterTypes[index];
					EventTypeExpression typeShardingKeyEl = 
							Arrays.asList(annotations[index])
							.stream()
							.filter(annot -> annot.annotationType() == ShardingKeyExpression.class)
							.findAny()
							.map(annot -> (ShardingKeyExpression)annot)
							.map(annt -> {
								return new EventTypeExpression(
										annt.el(), 
										annt.filterEl());
							})
							.orElse(commonShardingKeyExpression);
					if (typeShardingKeyEl == null) {
						return null;
					}
					return Pair.of(
							parType.getSimpleName(),
							typeShardingKeyEl);
				})
				.filter(Objects::nonNull)
				.collect(
						Collectors.toMap(
								Pair::getKey, 
								Pair::getValue));
		return expressionMap;
	}
	
	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class EventTypeExpression {
		private String shardingValEl;
		private String filterEl;
	}
	
	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class EventBatchConfig {
		private Duration timeWindow;
		private int batchSize;
	}

	private EventSynchronizer findDistributedJobAnnotations(Method method) {
		return AnnotationUtils.getAnnotation(method, EventSynchronizer.class);
	}
	
}