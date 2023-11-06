package com.zero.ddd.akka.cluster.core.helper;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2021-11-26 05:33:32
 * @Desc 些年若许,不负芳华.
 *
 */
public class JdbcEnvrCondition implements Condition {

	@Override
	public boolean matches(
			ConditionContext context, 
			AnnotatedTypeMetadata metadata) {
		String systemStatusPath = 
				context.getEnvironment()
				.getProperty("management.endpoints.web.base-path");
		return systemStatusPath != null;
	}

}

