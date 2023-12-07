package com.zero.ddd.akka.cluster.job.processor;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.aop.support.AopUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.ApplicationContext;
import org.springframework.core.MethodIntrospector;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.lang.Nullable;

import com.google.common.collect.Sets;
import com.zero.ddd.akka.cluster.job.annotations.DistributedJob;
import com.zero.ddd.akka.cluster.job.annotations.JobRetry;
import com.zero.ddd.akka.cluster.job.annotations.JobScheduleTimeout;
import com.zero.ddd.akka.cluster.job.annotations.JobScheduled;
import com.zero.ddd.akka.cluster.job.definition.JobEndpoint;
import com.zero.ddd.akka.cluster.job.definition.JobMethodInvoker;
import com.zero.ddd.akka.cluster.job.definition.JobMethodInvokerImplByBeanInvoker;
import com.zero.ddd.akka.cluster.job.definition.JobRetryConfig;
import com.zero.ddd.akka.cluster.job.definition.JobScheduleTimeoutConfig;
import com.zero.ddd.akka.cluster.job.definition.JobScheduledConfig;
import com.zero.ddd.akka.cluster.job.definition.JobScheudleTimeoutListener;
import com.zero.ddd.akka.cluster.job.definition.JobTaskContext;
import com.zero.ddd.akka.cluster.job.definition.TaskResult;
import com.zero.ddd.akka.cluster.job.definition.listener.JustLogJobScheudleTimeoutListener;
import com.zero.helper.GU;
import com.zero.helper.beanInvoker.BeanInvokeUtils;
import com.zero.helper.beanInvoker.BeanMethodInvoke;
import com.zero.helper.beanInvoker.Invoker;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2023-04-21 07:22:36
 * @Desc 些年若许,不负芳华.
 * 
 * 解析出所有的标注了DistributedTask注解的方法，分组出Job, JobWorker
 *
 */
@Slf4j(topic = "job")
@RequiredArgsConstructor
public class JobBeanProcessor implements BeanPostProcessor {
	
	private final Set<Class<?>> nonAnnotatedClasses = Collections.newSetFromMap(new ConcurrentHashMap<>(64));
	
	private final ApplicationContext applicationContext;
	private final DistributedJobEndpointRegister register;

	@Override
	@Nullable
	public Object postProcessAfterInitialization(
			Object bean, 
			String beanName) throws BeansException {
		if (!this.nonAnnotatedClasses.contains(bean.getClass())) {
			Class<?> targetClass = AopUtils.getTargetClass(bean);
			Map<Method, Set<DistributedJob>> annotatedMethods = 
					MethodIntrospector.selectMethods(
							targetClass,
							(MethodIntrospector.MetadataLookup<Set<DistributedJob>>) method -> {
								DistributedJob jobAnnotation = 
										this.findDistributedJobAnnotations(method);
								return (jobAnnotation == null ? 
										null : Sets.newHashSet(jobAnnotation));
							});
			if (annotatedMethods.isEmpty()) {
				this.nonAnnotatedClasses.add(bean.getClass());
			} else {
				for (Map.Entry<Method, Set<DistributedJob>> entry : annotatedMethods.entrySet()) {
					Method method = entry.getKey();
					for (DistributedJob listener : entry.getValue()) {
						processDistributedJob(listener, method, bean, beanName);
					}
				}
			}
		}
		return bean;
	}

	@SuppressWarnings("deprecation")
	private void processDistributedJob(
			DistributedJob distributedJob, 
			Method method, 
			Object bean,
			String beanName) {
		try {
			this.register.registe(
					JobEndpoint.builder()
					.jobName((
							GU.isNullOrEmpty(distributedJob.jobName()) ? 
									method.getName() : distributedJob.jobName())
							.trim())
					.jobShowName(
							distributedJob.jobShowName().trim())
					.jobParams(distributedJob.jobParams())
					.jobExecuteType(distributedJob.jobExecuteType())
					.workerConcurrency(distributedJob.concurrency())
					.waitMinWorkerCount(
							distributedJob.minWorkerCount())
					.jobScheduleTimeout(
							this.parseJobScheduleTimeConfig(
									distributedJob.jobScheduleTimeout()))
					.jobRetryConfig(
							this.parseJobRetryConfig(
									distributedJob.jobRetry()))
					.jobScheduledConfig(
							this.parseJobScheduledConfig(
									distributedJob.jobScheduled()))
					.jobMethodInvoker(
							this.parseJobTaskContextConsumer(bean, method))
					.build());
		} catch (IllegalArgumentException ex) {
			throw new IllegalStateException(
					"beanName:[" + beanName + "] 调度方法:[" + method.getName() + "] 注册为分布式调度任务失败[" + ex.getMessage() + "]");
		}
	}
	
	private Optional<JobScheduleTimeoutConfig> parseJobScheduleTimeConfig(
			JobScheduleTimeout jobScheduleTimeout) {
		return 
				Optional.ofNullable(jobScheduleTimeout)
				.filter(timeout -> timeout.timeout() > 0)
				.map(timeout -> {
					return 
							new JobScheduleTimeoutConfig(
									Duration.ofMillis(
											timeout.timeoutUnit().toMillis(
													timeout.timeout())), 
									parseTimeoutListener(
											timeout.timeoutListener()));
				});
	}
	
	private JobScheudleTimeoutListener parseTimeoutListener(
			Class<? extends JobScheudleTimeoutListener> timeoutListener) {
		if (timeoutListener == JustLogJobScheudleTimeoutListener.class) {
			return JustLogJobScheudleTimeoutListener.JUST_LOG_TIMEOUT_LISTENER;
		}
		Optional<? extends JobScheudleTimeoutListener> bean = 
				this.getBean(timeoutListener);
		if (bean.isPresent()) {
			return bean.get();
		} else {
			return JustLogJobScheudleTimeoutListener.JUST_LOG_TIMEOUT_LISTENER;
		}
	}

	@SuppressWarnings("unchecked")
	private JobMethodInvoker parseJobTaskContextConsumer(
			Object bean,
			Method consumerMethod) {
		Class<?> sourceClass = 
				AopUtils.getTargetClass(bean);
		if (!Modifier.isPublic(consumerMethod.getModifiers())) {
			throw new IllegalStateException(
					"Bean:[" + sourceClass.getName() + "]调度方法:[" + consumerMethod.getName() + "] 无法进行调用，请检查修饰符为public");
		}
		if (!consumerMethod.getReturnType().equals(TaskResult.class)) {
			throw new IllegalStateException(
					"Bean:[" + sourceClass.getName() + "]调度方法:[" + consumerMethod.getName() + "] 需要以[" + TaskResult.class.getName() + "]作为返回类型");
		}
		BeanMethodInvoke<Object> beanMethodInovker = 
				(BeanMethodInvoke<Object>) BeanInvokeUtils.findBeanMethodInovker(
						sourceClass, 
						null, 
						method -> !method.isAnnotationPresent(DistributedJob.class));
		Class<?>[] parameterTypes = 
				consumerMethod.getParameterTypes();
		if (!filterExistsJobTaskContext(parameterTypes)) {
			throw new IllegalStateException(
					"Bean:[" + sourceClass.getName() + "]调度方法:[" + consumerMethod.getName() + "] 参数列表需要包含[" + JobTaskContext.class.getName() + "]类型");
		}
		Invoker<Object> methodInvoker = 
				beanMethodInovker.getMethodInvoker(
						consumerMethod.getName(),
						parameterTypes);
		return new JobMethodInvokerImplByBeanInvoker(
				bean, 
				parameterTypes, 
				methodInvoker);
	}

	private boolean filterExistsJobTaskContext(
			Class<?>[] parameterTypes) {
		return Arrays.asList(parameterTypes)
				.stream()
				.filter(clazz -> {
					return clazz == JobTaskContext.class;
				})
				.findAny()
				.isPresent();
	}

	private JobScheduledConfig parseJobScheduledConfig(
			JobScheduled jobScheduled) {
		return Optional.ofNullable(jobScheduled)
				.filter(sch -> sch.useing())
				.map(sch -> {
					return new JobScheduledConfig(
							jobScheduled.initialDelay(), 
							jobScheduled.cron(),
							jobScheduled.fixedDelay() > 0 ? 
									Duration.ofMillis(jobScheduled.fixedDelay()) : null);
				})
				.orElse(null);
	}

	private Optional<JobRetryConfig> parseJobRetryConfig(
			JobRetry jobRetry) {
		return Optional.ofNullable(jobRetry)
				.filter(retry -> retry.useing())
				.map(retry -> {
					return new JobRetryConfig(
							Duration.ofMillis(retry.minBackoffMill()), 
							Duration.ofMillis(retry.maxBackoffMill()), 
							retry.randomFactor(), 
							retry.maxRetries());
				});
	}

	private DistributedJob findDistributedJobAnnotations(Method method) {
		return AnnotationUtils.getAnnotation(method, DistributedJob.class);
	}
	
	private <T> Optional<T> getBean(Class<T> requiredType) {
		try {
			return Optional.ofNullable(
					this.applicationContext.getBean(
							requiredType));
		} catch (Exception e) {
			log.warn("NoSuchBean with type:{}", requiredType.getName());
		}
		return Optional.empty();
	}

}