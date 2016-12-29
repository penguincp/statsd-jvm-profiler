package com.etsy.statsd.profiler.profilers;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryType;
import java.lang.management.MemoryUsage;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import com.etsy.statsd.profiler.Arguments;
import com.etsy.statsd.profiler.Profiler;
import com.etsy.statsd.profiler.reporter.Reporter;
import com.etsy.statsd.profiler.util.TagUtil;
import com.google.common.collect.Maps;

/**
 * Profiles memory usage and GC statistics
 *
 * @author Andrew Johnson
 */
public class MemoryProfiler extends Profiler {
	private long period = 10;

	private final MemoryMXBean memoryMXBean;
	private final List<GarbageCollectorMXBean> gcMXBeans;
	private final HashMap<GarbageCollectorMXBean, AtomicLong> gcTimes = new HashMap<>();
	//	private final ClassLoadingMXBean classLoadingMXBean;
	//	private final List<MemoryPoolMXBean> memoryPoolMXBeans;

	public MemoryProfiler(Reporter reporter, Arguments arguments) {
		super(reporter, arguments);
		memoryMXBean = ManagementFactory.getMemoryMXBean();
		gcMXBeans = ManagementFactory.getGarbageCollectorMXBeans();
		//		classLoadingMXBean = ManagementFactory.getClassLoadingMXBean();
		//		memoryPoolMXBeans = ManagementFactory.getMemoryPoolMXBeans();

		for (GarbageCollectorMXBean b : gcMXBeans) {
			gcTimes.put(b, new AtomicLong());
		}

		this.period = arguments.getIntArgument("MemoryProfiler-period");
		if (this.period == -1) {
			this.period = arguments.getIntArgument("period");
		}
		if (this.period == -1) {
			this.period = 10;
		}

	}

	/**
	 * Profile memory usage and GC statistics
	 */
	@Override
	public void profile() {
		recordStats();
	}

	@Override
	public void flushData() {
		recordStats();
	}

	@Override
	public long getPeriod() {
		return period;
	}

	@Override
	public TimeUnit getTimeUnit() {
		return TimeUnit.SECONDS;
	}

	@Override
	protected void handleArguments(Arguments arguments) {
		/* No arguments needed */ }

	/**
	 * Records all memory statistics
	 */
	private void recordStats() {

		MemoryUsage heap = memoryMXBean.getHeapMemoryUsage();
		MemoryUsage nonHeap = memoryMXBean.getNonHeapMemoryUsage();
		Map<String, Long> metrics = Maps.newHashMap();

		//        recordMemoryUsage("heap.total", heap, metrics);
		//        recordMemoryUsage("nonheap.total", nonHeap, metrics);

		recordMemoryUsage("heap" + TagUtil.TAG_SEPARATOR_SB + "total", heap, metrics);
		recordMemoryUsage("nonheap" + TagUtil.TAG_SEPARATOR_SB + "total", nonHeap, metrics);

		recordGcUsage(metrics);

		//TODO: need to gather these?
		//		long loadedClassCount = classLoadingMXBean.getLoadedClassCount();
		//		long totalLoadedClassCount = classLoadingMXBean.getTotalLoadedClassCount();
		//		long unloadedClassCount = classLoadingMXBean.getUnloadedClassCount();

		//		long finalizationPendingCount = memoryMXBean.getObjectPendingFinalizationCount();
		//		metrics.put("pending-finalization-count", finalizationPendingCount);
		//		metrics.put("loaded-class-count", loadedClassCount);
		//		metrics.put("total-loaded-class-count", totalLoadedClassCount);
		//		metrics.put("unloaded-class-count", unloadedClassCount);

		//TODO: need to gather these?
		//		for (MemoryPoolMXBean memoryPoolMXBean : memoryPoolMXBeans) {
		//			String type = poolTypeToMetricName(memoryPoolMXBean.getType());
		//			String name = poolNameToMetricName(memoryPoolMXBean.getName());
		//			String prefix = type + '.' + name;
		//			MemoryUsage usage = memoryPoolMXBean.getUsage();
		//
		//			recordMemoryUsage(prefix, usage, metrics);
		//		}

		recordGaugeValues(metrics);
	}

	private void recordGcUsage(Map<String, Long> metrics) {
		for (GarbageCollectorMXBean gcMXBean : gcMXBeans) {
			String gcName = gcMXBean.getName().replace(" ", "_");
			long count = gcMXBean.getCollectionCount();
			if (count > 0) {
				metrics.put("gc" + TagUtil.TAG_SEPARATOR_SB + gcName + ".count", count);
			}

			final long time = gcMXBean.getCollectionTime();
			final long prevTime = gcTimes.get(gcMXBean).get();
			final long runtime = time - prevTime;

			if (time > 0) {
				metrics.put("gc" + TagUtil.TAG_SEPARATOR_SB + gcName + ".time", time);
			}
			if (runtime > 0) {
				metrics.put("gc" + TagUtil.TAG_SEPARATOR_SB + gcName + ".runtime", runtime);
				gcTimes.get(gcMXBean).set(time);
			}

			//			metrics.put("gc" + TagUtil.TAG_SEPARATOR_SB + gcName + ".count", count);
			//			metrics.put("gc" + TagUtil.TAG_SEPARATOR_SB + gcName + ".time", time);
			//			metrics.put("gc" + TagUtil.TAG_SEPARATOR_SB + gcName + ".runtime", runtime);

		}
	}

	/**
	 * Records memory usage
	 *
	 * @param prefix The prefix to use for this object
	 * @param memory The MemoryUsage object containing the memory usage info
	 */
	private static void recordMemoryUsage(String prefix, MemoryUsage memory,
			Map<String, Long> metrics) {
		metrics.put(prefix + ".init", memory.getInit());
		metrics.put(prefix + ".used", memory.getUsed());
		metrics.put(prefix + ".committed", memory.getCommitted());
		metrics.put(prefix + ".max", memory.getMax());
	}

	/**
	 * Formats a MemoryType into a valid metric name
	 *
	 * @param memoryType a MemoryType
	 * @return a valid metric name
	 */
	private static String poolTypeToMetricName(MemoryType memoryType) {
		switch (memoryType) {
		case HEAP:
			return "heap";
		case NON_HEAP:
			return "nonheap";
		default:
			return "unknown";
		}
	}

	/**
	 * Formats a pool name into a valid metric name
	 *
	 * @param poolName a pool name
	 * @return a valid metric name
	 */
	private static String poolNameToMetricName(String poolName) {
		return poolName.toLowerCase().replaceAll("\\s+", "-");
	}
}
