package com.zero.ddd.core.helper;

import java.io.Closeable;
import java.text.NumberFormat;
import java.util.Collection;
import java.util.Comparator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * @return
 */
public class ConcurrentStopWatch implements Closeable {
	
	private final String name;
	private final AtomicLong totalNanos = new AtomicLong();
	private final CopyOnWriteArrayList<StopWatchItem> stopedTask = new CopyOnWriteArrayList<>();
	private final ConcurrentHashMap<String, StopWatchItem> allSubThreadTask = new ConcurrentHashMap<>();
	
	public ConcurrentStopWatch() {
		this("");
	}

	public ConcurrentStopWatch(
			final String name) {
		this.name = name;
	}

	public void start(
			String taskName) {
		String thId = Thread.currentThread().getName();
		if (allSubThreadTask.containsKey(thId)) {
			throw new IllegalStateException(
					"Task:" + allSubThreadTask.get(thId).getName() + " 运行中，请勿再次启动");
		} else {
			allSubThreadTask.put(
					thId, 
					new StopWatchItem(taskName).start());
		}
	}

	public void stop() {
		String thId = Thread.currentThread().getName();
		if (!allSubThreadTask.containsKey(thId)) {
			throw new IllegalStateException("当前线程无运行中的子任务记录");
		} else {
			StopWatchItem stop = 
					allSubThreadTask.remove(thId).stop();
			totalNanos.addAndGet(stop.getTimeNanos());
			stopedTask.add(stop);
		}
	}

	public String prettyPrint() {
		StringBuilder sb = new StringBuilder(shortSummary());
		sb.append('\n');
		sb.append("---------------------------------------------\n");
		sb.append("ns         %     Task name\n");
		sb.append("---------------------------------------------\n");
		NumberFormat nf = NumberFormat.getNumberInstance();
		nf.setMinimumIntegerDigits(9);
		nf.setGroupingUsed(false);
		NumberFormat pf = NumberFormat.getPercentInstance();
		pf.setMinimumIntegerDigits(3);
		pf.setGroupingUsed(false);
		for (StopWatchItem task : getTaskList()) {
			sb.append(nf.format(task.getTimeNanos())).append("  ");
			sb.append(pf.format((double) task.getTimeNanos() / getTotalTimeNanos())).append("  ");
			sb.append(task.getName()).append("\n");
		}
		return sb.toString();
	}

	private double getTotalTimeNanos() {
		return this.totalNanos.doubleValue();
	}

	private Collection<StopWatchItem> getTaskList() {
		return this.stopedTask
				.stream()
				.sorted(Comparator.comparing(StopWatchItem::getTimeNanos))
				.collect(Collectors.toList());
	}

	/**
	 */
	public String shortSummary() {
		return "StopWatch '" + this.name + "': running time = " + getTotalTimeNanos() + " ns";
	}
	
	public void close() {
		this.allSubThreadTask.clear();
		this.stopedTask.clear();
	}
	
	@RequiredArgsConstructor
	private static class StopWatchItem {
		@Getter
		private final String name;
		private long startAtNs;
		private long endAtNs;
		
		StopWatchItem start() {
			this.startAtNs = System.nanoTime();
			return this;
		}
		
		public long getTimeNanos() {
			return this.endAtNs - this.startAtNs;
		}

		StopWatchItem stop() {
			this.endAtNs = System.nanoTime();
			return this;
		}
	}
	
}