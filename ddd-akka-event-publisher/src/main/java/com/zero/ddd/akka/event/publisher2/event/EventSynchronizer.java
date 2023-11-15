package com.zero.ddd.akka.event.publisher2.event;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.tuple.Pair;

import com.zero.ddd.akka.event.publisher2.beanProcessor.EventSynchronizerBeanProcessor.EventTypeExpression;
import com.zero.helper.GU;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2023-03-07 02:21:45
 * @Desc 些年若许,不负芳华.
 *
 */
@Getter
@Builder
@ToString
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class EventSynchronizer {
	
	private String appName;
	private int partition;
	private String synchornizerId;
	private Set<String> awareEventTypes;
	private Map<String, EventTypeExpression> typeShardingHashValExpression;
	
	public EventSynchronizer(
			String appName, 
			int partition,
			String synchornizerId, 
			Set<String> awareEventTypes) {
		super();
		this.appName = appName;
		this.partition = partition;
		this.synchornizerId = synchornizerId;
		this.awareEventTypes = awareEventTypes;
	}
	
	public String uniqueKey() {
		return this.appName + "-" + this.synchornizerId;
	}
	
	public Map<String, TypeFilterAndShardingHashValGenerator> typeShardingHashValGenerator() {
		return Optional.ofNullable(
				this.typeShardingHashValExpression)
				.map(expressions -> {
					return expressions.entrySet()
							.stream()
							.map(entry -> {
								return 
										Pair.of(
												entry.getKey(), 
												new TypeFilterAndShardingHashValGenerator(entry.getValue()));
							})
							.collect(Collectors.toMap(Pair::getKey, Pair::getValue));
				})
				.orElse(GU.emptyMap());
	}
	
}