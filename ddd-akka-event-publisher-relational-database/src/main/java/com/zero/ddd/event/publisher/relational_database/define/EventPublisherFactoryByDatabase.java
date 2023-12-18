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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;

import javax.annotation.PreDestroy;

import org.apache.commons.lang3.RandomUtils;
import org.apache.commons.lang3.tuple.Pair;
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
	private final String storedEventHasMoreSameTimeLoadEventSql;
	
	private final Duration storedEventPullDuration;
	private final Duration partitionEventPollDuration;
	private final NamedParameterJdbcTemplate jdbcTemplate;
	private final ExponentialBackOff backoffForRetryEventLoad;
	private final ScheduledExecutorService scheduledExecutorService;
	private final RowMapper<StoredEventTimeWrapper> storedEventRowMapper;
	private final RowMapper<PartitionStoredEventTimeWrapper> partitionEventRowMapper;
	
	private final ConcurrentHashMap<String, ScheduledFuture<?>> cancenFutureMap = new ConcurrentHashMap<>();
	
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
						"select count(1) from %s where type_name in(:awareTypes) and insert_time > :insertTime", 
						storedEventTable);
		this.storedEventHasMoreSameTimeLoadEventSql = 
				String.format(
						"select count(1) from %s where type_name in(:awareTypes) and insert_time = :insertTime", 
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
		return 20000;
	}
	
	@PreDestroy
	public void onExit() {
		this.scheduledExecutorService.shutdownNow();
	}
	
	@Override
	public Publisher<StoredEventWrapper> storedEventPublisher(
			String eventSynchronizerId,
			Optional<String> startAfterOffset,
			Set<String> awareEventTypes) {
		MapSqlParameterSource parameterSource = 
				new MapSqlParameterSource(
						"awareTypes", 
						awareEventTypes);
		String optDesc = eventSynchronizerId;
		final StoredEventOffset storedEventOffset = 
				new StoredEventOffset(
						startAfterOffset
						.map(timeStr -> LocalDateTime.parse(timeStr, TIME_FORMAT))
						.orElseGet(LocalDateTime::now));
		this.monitorEventOffsetDuringStuck(
				eventSynchronizerId,
				storedEventOffset,
				awareEventTypes);
		final ThreadSafeRoaring64Bitmap storedEventBitMap = new ThreadSafeRoaring64Bitmap();
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
		ThreadSafeRoaring64Bitmap partitionStoredEventBitMap = new ThreadSafeRoaring64Bitmap();
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
	
	private void monitorEventOffsetDuringStuck(
			String eventSynchronizerId, 
			StoredEventOffset storedEventOffset,
			Set<String> awareEventTypes) {
		if (cancenFutureMap.containsKey(eventSynchronizerId)) {
			return;
		}
		MapSqlParameterSource parameterSource = 
				new MapSqlParameterSource(
						"awareTypes", 
						awareEventTypes);
		Function<LocalDateTime, Pair<Boolean, Integer>> hasMoreEventJuedger = 
				time -> {
					if (time == null) {
						return Pair.of(false, 0);
					}
					parameterSource.addValue(INSERT_TIME, time);
					Integer queryForObject = 
							this.jdbcTemplate.queryForObject(
									storedEventHasMoreLoadEventSql, 
									parameterSource, 
									Integer.class);
					if (queryForObject == null
							|| queryForObject.intValue() == 0) {
						queryForObject = 
								this.jdbcTemplate.queryForObject(
										this.storedEventHasMoreSameTimeLoadEventSql, 
										parameterSource, 
										Integer.class);
						return 
								Pair.of(
										queryForObject != null && queryForObject > 1, 
										queryForObject);
					}
					return 
							Pair.of(
									queryForObject != null && queryForObject > 0, 
									queryForObject);
				
				};
		int randomScheduleFixedDelay = 
				RandomUtils.nextInt(5555, 8888);
		int storedEventLoadBatch = this.storedEventLoadBatch();
		AtomicBoolean duringWarning = new AtomicBoolean(false);
		ScheduledFuture<?> scheduleWithFixedDelay = 
				this.scheduledExecutorService.scheduleWithFixedDelay(
						() -> {
							try {
								Pair<Boolean, Integer> countRes = null;
								LocalDateTime lastSyncTime = storedEventOffset.getLastSyncTime();
								if (storedEventOffset.juedgeDuringLoadStuckAndResetLoadToGtLastSyncTime()
										&& (countRes = hasMoreEventJuedger.apply(lastSyncTime)).getLeft()) {
									log.warn(
											"[{}] 从DDD_STORED_EVENT加载新事件可能hang住了, 最后事件同步时间戳:[{}], 后续堆积事件数:[{}] 当前storedEventLoadBatch:{} 请检查事件QPS是否过大(可能需要手动调整改值，抱歉)", 
											eventSynchronizerId, 
											lastSyncTime,
											countRes.getRight(),
											storedEventLoadBatch);
									duringWarning.set(true);
								} else if (duringWarning.get()){
									log.warn(
											"[{}] 从DDD_STORED_EVENT加载新事件从【可能的hang住状态】恢复了, 最新事件同步时间戳:[{}]. 当前storedEventLoadBatch:{} ", 
											eventSynchronizerId, 
											lastSyncTime,
											storedEventLoadBatch);
									duringWarning.set(false);
								}
							} catch (Exception e) {
								log.error("monitorEventOffsetDuringStuck error:{}", e);
							}
						}, 
						randomScheduleFixedDelay, 
						randomScheduleFixedDelay,
						TimeUnit.MILLISECONDS);
		this.cancenFutureMap.put(eventSynchronizerId, scheduleWithFixedDelay);
		log.info("事件同步器:[{}] 开启加载表记录卡顿检查任务", eventSynchronizerId);
	}

	@Override
	public void storedEventPublisherShutdown(
			String eventSynchronizerId) {
		EventPublisherFactory.super.storedEventPublisherShutdown(eventSynchronizerId);
		Optional.ofNullable(
				this.cancenFutureMap.remove(
						eventSynchronizerId))
		.ifPresent(future -> {
			future.cancel(true);
			log.info("事件同步器:[{}] 停止加载表记录卡顿检查任务", eventSynchronizerId);
		});
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
		
		public synchronized boolean juedgeDuringLoadStuckAndResetLoadToGtLastSyncTime() {
			/**
			 * 如果3333~6666的毫秒周期内，事件的最后同步时间没有发生变化, 可能出现了卡顿
			 * 
			 * e.g. |------(5000-|-5000)-5000-|
			 * 			   !0    !1	   !2
			 * 假设每次加载1万的事件数
			 * !1位置执行加载，获取到的事件范围为!0到!2的1万事件数，执行完毕后，lastSyncTime=!2。
			 * lastSyncTime从!2位置时，往前推1秒，
			 * 即 where insertTime >= (lastSyncTime-1秒), 获取的事件数量为1万，这一秒内的事件数也是1万，导致lastSyncTime无法往后更新，一直停留在!2的位置
			 * 判断存在卡顿后，直接让加载流程从lastSyncTime往后加载，即加载条件变更为:where insertTime >= lastSyncTime, 
			 * 同样的，如果lastSyncTime(毫秒精度)这同一时间点内的事件数超过1万，会继续导致卡顿。但一毫秒内的事件数超过{storedEventLoadBatch}的值，目前框架不考虑，😝。
			 * 每次加载的事件数量可通过{storedEventLoadBatch}调整，目前默认为3万。
			 */
			if (freezedMonitorDateTime == null
					|| freezedMonitorDateTime.isBefore(this.lastSyncTime)) {
				this.freezedMonitorDateTime = this.lastSyncTime;
				return false;
			} else {
				this.startFromCurrent = true;
				return true;
			}
		}

		public synchronized void updateLastSyncTime(
				LocalDateTime eventOffset) {
			this.lastSyncTime = eventOffset;
		}
		
		public synchronized LocalDateTime getLastSyncTime() {
			return 
					this.lastSyncTime;
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
	
	public static class ThreadSafeRoaring64Bitmap {
		
		private Roaring64Bitmap bitMap = new Roaring64Bitmap();

		public synchronized boolean contains(long eventIdVal) {
			return 
					this.bitMap.contains(eventIdVal);
		}

		public synchronized void addLong(long eventIdVal) {
			this.bitMap.addLong(eventIdVal);
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