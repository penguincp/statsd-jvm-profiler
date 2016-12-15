package com.etsy.statsd.profiler.profilers;

import java.util.concurrent.TimeUnit;

import com.etsy.statsd.profiler.Arguments;
import com.etsy.statsd.profiler.Profiler;
import com.etsy.statsd.profiler.reporter.Reporter;

/**
 * Mock profiler for testing
 *
 * @author Andrew Johnson
 */
public class MockProfilerWithArguments extends Profiler {
	public static final String FAKE_ARG = "fakeArg";

	public String fake;

	public MockProfilerWithArguments(Reporter reporter, Arguments arguments) {
		super(reporter, arguments);
	}

	@Override
	public void profile() {
	}

	@Override
	public void flushData() {
	}

	@Override
	public long getPeriod() {
		return 0;
	}

	@Override
	public TimeUnit getTimeUnit() {
		return null;
	}

	@Override
	protected void handleArguments(Arguments arguments) {
		fake = arguments.getStringArgument(FAKE_ARG);
	}
}
