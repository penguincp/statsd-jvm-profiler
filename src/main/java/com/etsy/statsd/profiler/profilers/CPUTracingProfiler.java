package com.etsy.statsd.profiler.profilers;

import java.lang.management.ThreadInfo;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.etsy.statsd.profiler.Arguments;
import com.etsy.statsd.profiler.Profiler;
import com.etsy.statsd.profiler.reporter.Reporter;
import com.etsy.statsd.profiler.util.CPUTraces;
import com.etsy.statsd.profiler.util.Range;
import com.etsy.statsd.profiler.util.StackTraceFilter;
import com.etsy.statsd.profiler.util.StackTraceFormatter;
import com.etsy.statsd.profiler.util.ThreadDumper;
import com.etsy.statsd.profiler.util.TimeUtil;
import com.etsy.statsd.profiler.worker.ProfilerThreadFactory;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

/**
 * Profiles CPU time spent in each method
 *
 * @author Andrew Johnson
 */
public class CPUTracingProfiler extends Profiler {
	private static final String PACKAGE_WHITELIST_ARG = "packageWhitelist";
	private static final String PACKAGE_BLACKLIST_ARG = "packageBlacklist";

	public static final long REPORTING_PERIOD = 1;
	public static final long PERIOD = 10;
	public static final List<String> EXCLUDE_PACKAGES = Arrays.asList("com.etsy.statsd.profiler",
			"com.timgroup.statsd");

	private final CPUTraces traces;
	private long profileCount;
	private StackTraceFilter filter;
	private final long reportingFrequency;

	public CPUTracingProfiler(Reporter reporter, Arguments arguments) {
		super(reporter, arguments);
		traces = new CPUTraces();
		profileCount = 0;
		reportingFrequency = TimeUtil.convertReportingPeriod(getPeriod(), getTimeUnit(),
				REPORTING_PERIOD, TimeUnit.SECONDS);
	}

	/**
	 * Profile CPU time by method call
	 */
	@Override
	public void profile() {
		profileCount++;

		for (ThreadInfo thread : getAllRunnableThreads()) {
			// certain threads do not have stack traces
			if (thread.getStackTrace().length > 0) {
				String traceKey = StackTraceFormatter.formatStackTrace(thread.getStackTrace());
				if (filter.includeStackTrace(traceKey)) {
					traces.increment(traceKey, 1);
				}
			}
		}

		// To keep from overwhelming StatsD, we only report statistics every second
		if (profileCount == reportingFrequency) {
			profileCount = 0;
			recordMethodCounts();
		}
	}

	/**
	 * Flush methodCounts data on shutdown
	 */
	@Override
	public void flushData() {
		recordMethodCounts();
		// These bounds are recorded to help speed up generating flame graphs for certain backends
		if (emitBounds()) {
			Range bounds = traces.getBounds();
			recordGaugeValue("cpu.trace." + bounds.getLeft(), bounds.getLeft());
			recordGaugeValue("cpu.trace." + bounds.getRight(), bounds.getRight());
		}
	}

	@Override
	public long getPeriod() {
		return PERIOD;
	}

	@Override
	public TimeUnit getTimeUnit() {
		return TimeUnit.MILLISECONDS;
	}

	@Override
	protected void handleArguments(Arguments arguments) {
		List<String> packageWhitelist = parsePackageList(
				arguments.getStringArgument(PACKAGE_WHITELIST_ARG));
		List<String> packageBlacklist = parsePackageList(
				arguments.getStringArgument(PACKAGE_BLACKLIST_ARG));
		filter = new StackTraceFilter(packageWhitelist,
				Lists.newArrayList(Iterables.concat(EXCLUDE_PACKAGES, packageBlacklist)));
	}

	/**
	 * Parses a colon-delimited list of packages
	 *
	 * @param packages A string containing a colon-delimited list of packages
	 * @return A List of packages
	 */
	private static List<String> parsePackageList(String packages) {
		if (packages == null) {
			return new ArrayList<>();
		} else {
			return Arrays.asList(packages.split(":"));
		}
	}

	/**
	 * Records method CPU time in StatsD
	 */
	private void recordMethodCounts() {
		recordGaugeValues(traces.getDataToFlush());
	}

	/**
	 * Gets all runnable threads, excluding profiler threads
	 *
	 * @return A Collection<ThreadInfo> representing current thread state
	 */
	private static Collection<ThreadInfo> getAllRunnableThreads() {
		return ThreadDumper.filterAllThreadsInState(false, false, Thread.State.RUNNABLE,
				new Predicate<ThreadInfo>() {
					@Override
					public boolean apply(ThreadInfo input) {
						return !input.getThreadName().startsWith(ProfilerThreadFactory.NAME_PREFIX);
					}
				});
	}
}
