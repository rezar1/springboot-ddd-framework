package com.zero.ddd.event.publisher.relational_database.define;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;

import org.apache.commons.lang3.RandomUtils;
import org.apache.commons.lang3.StringUtils;
import org.reactivestreams.Publisher;
import org.roaringbitmap.longlong.Roaring64Bitmap;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.util.backoff.BackOffExecution;
import org.springframework.util.backoff.ExponentialBackOff;

import com.google.common.collect.Maps;
import com.zero.ddd.akka.event.publisher2.helper.MicrTimeFormat;
import com.zero.ddd.akka.event.publisher2.publisher.EventPublisherFactory;
import com.zero.ddd.core.event.store.StoredEvent;
import com.zero.helper.GU;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2023-07-03 11:45:17
 * @Desc 些年若许,不负芳华.
 *
 */
@Slf4j
public class EventPublisherFactoryByDatabase implements EventPublisherFactory {
	
	private static final String INSERT_TIME = "insertTime";
	private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS");
	
	
	private final String storedEventLoadSql;
	private final String storedEventHasMoreLoadEventSql;
	private final String partitionStoredEventLoadSql;
	private final Duration storedEventPullDuration;
	private final Duration partitionEventPollDuration;
	private final NamedParameterJdbcTemplate jdbcTemplate;
	private final ExponentialBackOff backoffForRetryEventLoad;
	private final ScheduledExecutorService scheduledExecutorService;
	private final RowMapper<StoredEventTimeWrapper> storedEventRowMapper;
	private final RowMapper<PartitionStoredEventTimeWrapper> partitionEventRowMapper;
	
	
	public EventPublisherFactoryByDatabase(
			NamedParameterJdbcTemplate jdbcTemplate,
			ScheduledExecutorService scheduledExecutorService,
			String storedEventTable,
			String partitionEventTable,
			Duration storedEventPollDuration,
			Duration partitionEventPollDuration) {
		this.jdbcTemplate = jdbcTemplate;
		this.scheduledExecutorService = scheduledExecutorService;
		this.storedEventPullDuration = storedEventPollDuration;
		this.partitionEventPollDuration = partitionEventPollDuration;
		this.storedEventLoadSql = 
				String.format(
						"select event_id, type_name, event_body, event_time, insert_time from %s where type_name in(:awareTypes) and insert_time >= :insertTime order by insert_time asc limit " + this.storedEventLoadBatch(), 
						storedEventTable);
		this.storedEventHasMoreLoadEventSql = 
				String.format(
						"select count(1) from %s where type_name in(:awareTypes) and insert_time >= :insertTime", 
						storedEventTable);
		this.partitionStoredEventLoadSql = 
				String.format(
						"select event_id, type_name, event_body, event_time, insert_time from %s where synchronizer_id = :eventSynchronizerId and partition_id = :partition and insert_time > :insertTime order by insert_time asc limit 1000", 
						partitionEventTable);
		this.storedEventRowMapper = this.dddStoredEventRowMapper();
		this.partitionEventRowMapper = this.partitionEventTableRowMapper();
		this.backoffForRetryEventLoad = this.initLoadEventFailureRetryBackoff();
	}
	
	protected int storedEventLoadBatch() {
		return 30000;
	}
	
	@Override
	public Publisher<StoredEventWrapper> storedEventPublisher(
			Optional<String> startAfterOffset,
			Set<String> awareEventTypes) {
		MapSqlParameterSource parameterSource = 
				new MapSqlParameterSource(
						"awareTypes", 
						awareEventTypes);
		String optDesc = 
				"storedEventPublisher:[" + StringUtils.join(awareEventTypes, ",") + "]";
		StoredEventOffset storedEventOffset = 
				new StoredEventOffset(
						startAfterOffset
						.map(timeStr -> LocalDateTime.parse(timeStr, TIME_FORMAT))
						.orElseGet(LocalDateTime::now));
		ScheduledFuture<?> scheduleWithFixedDelay = 
				this.monitorEventOffsetDuringStuck(
						optDesc,
						storedEventOffset,
						awareEventTypes);
		Roaring64Bitmap storedEventBitMap = new Roaring64Bitmap();
		return 
				Flux.interval(
						storedEventPullDuration.plus(
								Duration.ofMillis(
										RandomUtils.nextLong(
												1,
												180))))
				.onBackpressureDrop(ticket -> {
					log.warn("optDesc:[{}] 消费速率慢，丢弃当前轮次的调度，等待下一次调度", optDesc);
				})
				.doOnTerminate(() -> {
					scheduleWithFixedDelay.cancel(true);
					log.info("optDesc:[{}] Terminated", optDesc);
				})
				.flatMap(notUsed -> {
					parameterSource.addValue(
							INSERT_TIME, 
							storedEventOffset.lastSyncTime());
					return Flux.fromIterable(
							this.operationWithBackoffRetry(
									optDesc, 
									() -> {
										return 
												this.jdbcTemplate.query(
														storedEventLoadSql, 
														parameterSource , 
														storedEventRowMapper);
									}));
				})
				.onErrorResume(error -> {
					log.error("error:{}", error);
					return null;
				})
				.filter(Objects::nonNull)
				.filter(storedEvent -> {
					long eventIdVal = 
							Long.parseLong(
									storedEvent.getStoredEvent().getEventId());
					if (storedEventBitMap.contains(eventIdVal)) {
						return false;
					}
					storedEventBitMap.addLong(eventIdVal);
					storedEventOffset.updateLastSyncTime(
							storedEvent.getEventOffset());
					return true;
				})
				.map(event -> {
					return 
							new StoredEventWrapper(
									TIME_FORMAT.format(
											event.getEventOffset()),
									event.storedEvent);
				});
	}

	@Override
	public Publisher<PartitionStoredEventWrapper> partitionEventPublisher(
			String eventSynchronizerId, 
			int partition,
			Optional<String> startAfterOffset) {
		Roaring64Bitmap partitionStoredEventBitMap = new Roaring64Bitmap();
		AtomicReference<String> lastSyncTime = 
				new AtomicReference<String>(
						startAfterOffset
						.orElse(MicrTimeFormat.currentFormatTime()));
		Map<String,Object> paramMap = Maps.newHashMap();
		paramMap.put("eventSynchronizerId", eventSynchronizerId);
		paramMap.put("partition", partition);
		String optDesc = 
				"partitionEventPublisher:[" + eventSynchronizerId + "]-partition:" + partition;
		return 
				Flux.interval(
						partitionEventPollDuration.plus(
								Duration.ofMillis(
										RandomUtils.nextLong(
												1,
												180))))
				.onBackpressureDrop(ticket -> {
					log.warn("eventSynchronizerId:[{}] partition:[{}] 消费速率慢，丢弃当前轮次的调度，等待下一次调度", eventSynchronizerId, partition);
				})
				.doOnTerminate(() -> {
					log.info("optDesc:[{}] Terminated", optDesc);
				})
				.flatMap(notUsed -> {
					paramMap.put(INSERT_TIME, lastSyncTime.get());
					return Flux.fromIterable(
							this.operationWithBackoffRetry(
									optDesc, 
									() -> {
										return 
												this.jdbcTemplate.query(
														partitionStoredEventLoadSql, 
														paramMap , 
														partitionEventRowMapper);
									}));
				})
				.onErrorResume(error -> {
					log.error("error:{}", error);
					return null;
				})
				.filter(Objects::nonNull)
				.filter(storedEvent -> {
					long eventIdVal = 
							Long.parseLong(
									storedEvent.getStoredEvent().getEventId());
					if (partitionStoredEventBitMap.contains(eventIdVal)) {
						return false;
					}
					partitionStoredEventBitMap.addLong(eventIdVal);
					lastSyncTime.set(storedEvent.getEventOffset());
					return true;
				})
				.map(event -> {
					return 
							new PartitionStoredEventWrapper(
									event.getEventOffset(),
									event.storedEvent);
				});
	}
	
	protected ExponentialBackOff initLoadEventFailureRetryBackoff() {
		long initialInterval = 200;			// 初始间隔
		long maxInterval = 10 * 1000L;		// 最大间隔
		long maxElapsedTime = 600 * 1000L;	// 最大时间间隔
		double multiplier = 1.5;			// 递增倍数（即下次间隔是上次的多少倍）
		ExponentialBackOff backOff = 
				new ExponentialBackOff(
						initialInterval,
						multiplier);
		backOff.setMaxInterval(maxInterval);
		backOff.setMaxElapsedTime(maxElapsedTime);
		return backOff;
	}
	
	private ScheduledFuture<?> monitorEventOffsetDuringStuck(
			String optDesc, 
			StoredEventOffset storedEventOffset,
			Set<String> awareEventTypes) {
		MapSqlParameterSource parameterSource = 
				new MapSqlParameterSource(
						"awareTypes", 
						awareEventTypes);
		Function<LocalDateTime, Boolean> hasMoreEventJuedger = 
				time -> {
					parameterSource.addValue(INSERT_TIME, awareEventTypes);
					Integer queryForObject = 
							this.jdbcTemplate.queryForObject(
									storedEventHasMoreLoadEventSql, 
									parameterSource, 
									Integer.class);
					return 
							queryForObject != null && queryForObject > 0;
				};
		int random = RandomUtils.nextInt(3111, 6666);
		int storedEventLoadBatch = this.storedEventLoadBatch();
		return this.scheduledExecutorService.scheduleWithFixedDelay(
				() -> {
					try {
						LocalDateTime lastSyncTime = storedEventOffset.getLastSyncTime();
						if (storedEventOffset.juedgeDuringLoadStuck()
								&& hasMoreEventJuedger.apply(lastSyncTime)) {
							log.warn(
									"操作描述:[{}] 从DDD_STORED_EVENT加载新事件hang住了, 最后同步时间戳:{}. 当前storedEventLoadBatch:{} 请检查事件QPS是否过大, 适当调整该值", 
									optDesc, 
									lastSyncTime,
									storedEventLoadBatch);
						}
					} catch (Exception e) {
						e.printStackTrace();
					}
				}, 
				random, 
				random,
				TimeUnit.MILLISECONDS);
	}

	private RowMapper<StoredEventTimeWrapper> dddStoredEventRowMapper() {
		return 
				(rs, rowNum) -> {
					return 
							new StoredEventTimeWrapper(
									rs.getTimestamp("insert_time").toLocalDateTime(),
									this.parseStoredEvent(rs));
				};
	}
	
	private RowMapper<PartitionStoredEventTimeWrapper> partitionEventTableRowMapper() {
		return (rs, num) -> {
			return 
					new PartitionStoredEventTimeWrapper(
							rs.getString("insert_time"),
							parseStoredEvent(rs));
		};
	}
	
	private <T> List<T> operationWithBackoffRetry(
			String optDesc,
			Supplier<List<T>> optSupplier) {
		BackOffExecution execution = null;
		do {
			try {
				return 
						optSupplier.get();
			} catch (Exception e) {
				if (execution == null) {
					execution = backoffForRetryEventLoad.start();
				}
				long nextBackOff = execution.nextBackOff();
				log.error(
						"Op:[" + optDesc + "] error while operationWithBackoffRetry:{}, will retry with backoff:" + nextBackOff,
						e);
				if (nextBackOff == BackOffExecution.STOP) {
					break;
				}
				try {
					TimeUnit.MILLISECONDS.sleep(nextBackOff);
				} catch (InterruptedException e1) {
					break;
				}
			}
		} while(true);
		log.error("重试Opt:[" + optDesc + "]失败");
		return GU.emptyList();
	}
	
	private StoredEvent parseStoredEvent(
			ResultSet rs) throws SQLException {
		return new StoredEvent(
				rs.getLong("event_id") + "", 
				rs.getString("type_name"), 
				rs.getString("event_body"), 
				rs.getTimestamp("event_time").toLocalDateTime());
	}
	
	@Getter
	// 竞争不大
	public static class StoredEventOffset {
		
		private volatile LocalDateTime lastSyncTime;
		private volatile boolean startFromCurrent;
		
		private volatile LocalDateTime freezedMonitorDateTime;
		
		public StoredEventOffset(
				LocalDateTime lastSyncTime) {
			this.lastSyncTime = 
					lastSyncTime == null ?
							LocalDateTime.now() : lastSyncTime;
		}
		
		public synchronized boolean juedgeDuringLoadStuck() {
			if (freezedMonitorDateTime == null
					|| freezedMonitorDateTime.isBefore(this.lastSyncTime)) {
				this.freezedMonitorDateTime = this.lastSyncTime;
			} else {
				this.startFromCurrent = true;
			}
			return true;
		}

		public synchronized void updateLastSyncTime(
				LocalDateTime eventOffset) {
			this.lastSyncTime = eventOffset;
		}

		public synchronized LocalDateTime lastSyncTime() {
			try {
				return this.startFromCurrent ? 
						this.lastSyncTime : this.lastSyncTime.plusSeconds(-1);
			} finally {
				this.startFromCurrent = false;
			}
		}
		
	}
	
	@Data
	@AllArgsConstructor
	public static class StoredEventTimeWrapper {
		private final LocalDateTime eventOffset;
		private final StoredEvent storedEvent;
	}
	
	@Data
	@AllArgsConstructor
	public static class PartitionStoredEventTimeWrapper {
		private final String eventOffset;
		private final StoredEvent storedEvent;
	}

}