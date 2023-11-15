package com.zero.ddd.akka.event.publisher2.event;

import java.util.Map;
import java.util.Optional;

import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import com.zero.ddd.akka.event.publisher2.beanProcessor.EventSynchronizerBeanProcessor.EventTypeExpression;
import com.zero.ddd.core.helper.HashUtil;
import com.zero.helper.GU;
import com.zero.helper.JacksonUtil;

import lombok.extern.slf4j.Slf4j;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2023-06-29 12:15:24
 * @Desc 些年若许,不负芳华.
 *
 */
@Slf4j
public class TypeFilterAndShardingHashValGenerator {
	
	private static final SpelExpressionParser PARSER = new SpelExpressionParser();

	private static final long DEFAULT_PARTITION = 0l;
	private static final long FILTERED_PARTITION = -1l;
	
	private Optional<Expression> shardingValExpression;
	private Optional<Expression> filterTypeExpression;
	
	private final StandardEvaluationContext evaluationContext = new StandardEvaluationContext();
	
	public TypeFilterAndShardingHashValGenerator(
			EventTypeExpression elExpression) {
		this.shardingValExpression = 
				Optional.ofNullable(
						elExpression.getShardingValEl())
				.filter(GU::notNullAndEmpty)
				.map(PARSER::parseExpression);
		this.filterTypeExpression = 
				Optional.ofNullable(
						elExpression.getFilterEl())
				.filter(GU::notNullAndEmpty)
				.map(PARSER::parseExpression);
	}
	
	public long parseHashVal(
			String eventBody) {
		try {
			return 
					JacksonUtil.<Map<String, Object>>readValNoError(eventBody)
					.map(map -> {
						evaluationContext.setVariable("event", map);
						if (this.filterTypeExpression.isPresent()
								&& !this.filterTypeExpression.map(
										filterEp -> {
											Boolean value = 
													filterEp.getValue(
															evaluationContext, 
															Boolean.class);
											return value != null && value;
										})
										.orElse(false)) {
							return FILTERED_PARTITION;
						}
						return 
								this.shardingValExpression
								.map(expression -> {
									return 
											Math.abs(
													HashUtil.hashValue(
															expression.getValue(
																	evaluationContext)
															.toString()));
								})
								.orElse(DEFAULT_PARTITION);
					})
					.orElse(FILTERED_PARTITION);
		} catch (Exception e) {
			log.warn("error parseHashVal:{}", eventBody);
			log.error("error parseHashVal:{}" ,e);
		}
		return FILTERED_PARTITION;
	}

}