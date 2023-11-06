package com.zero.ddd.akka.cluster.job.definition;

import java.util.Arrays;
import java.util.function.Function;
import java.util.stream.IntStream;

import com.zero.helper.beanInvoker.Invoker;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2023-04-23 07:36:19
 * @Desc 些年若许,不负芳华.
 *
 */
public class JobMethodInvokerImplByBeanInvoker implements JobMethodInvoker {
	
	private final Object bean;
	private final Invoker<Object> invoker;
	private final Function<JobTaskContext, Object[]> reqMethodArgsFun;
	
	public JobMethodInvokerImplByBeanInvoker(
			Object bean,
			Class<?>[] parameterTypes,
			Invoker<Object> invoker) {
		this.bean = bean;
		this.invoker = invoker;
		int parameterSize = parameterTypes.length;
		int jobTaskContextParamIndex = 
				IntStream.range(0, parameterSize)
				.filter(index -> {
					return parameterTypes[index].equals(JobTaskContext.class);
				})
				.findAny()
				.getAsInt();
		this.reqMethodArgsFun = 
				context -> {
					Object[] args = new Object[parameterSize];
					Arrays.fill(args, null);
					args[jobTaskContextParamIndex] = context;
					return args;
				};
	}

	@Override
	public TaskResult invokeJobMethod(JobTaskContext context) {
		return 
				(TaskResult) this.invoker.invoke(
						this.bean, 
						this.reqMethodArgsFun.apply(
								context));
	}

}