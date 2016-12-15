package com.etsy.statsd.profiler.profilers;

import java.lang.management.ManagementFactory;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import com.etsy.statsd.profiler.Arguments;
import com.etsy.statsd.profiler.Profiler;
import com.etsy.statsd.profiler.reporter.Reporter;
import com.etsy.statsd.profiler.util.TagUtil;
import com.google.common.collect.Maps;

public class TomcatSessionProfiler extends Profiler {

	private String[] sessionBeans = new String[] {};
	private String[] sessionAtts = new String[] { "activeSessions", "rejectedSessions",
			"expiredSessions" };

	public TomcatSessionProfiler(Reporter reporter, Arguments arguments) {
		super(reporter, arguments);

		//don't place the following inside handleArguments(), which will be called from within the superclass' constructor, 
		//super constructor is first called, then this class' field initialization is done.
		//if the following is placed inside handleArguments(), sessionBeans will be reset to new String[]{}
		this.sessionBeans = arguments.getStringListArguments("tomcat.sessionBeans");
		String[] sessionAttrs = arguments.getStringListArguments("tomcat.sessionAttributes");
		if (sessionAttrs.length > 0) {
			this.sessionAtts = sessionAttrs;
		}

	}

	public static final long PERIOD = 10;

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
		return PERIOD;
	}

	@Override
	public TimeUnit getTimeUnit() {
		return TimeUnit.SECONDS;
	}

	@Override
	protected void handleArguments(Arguments arguments) {

	}

	private void recordStats() {
		MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
		Map<String, Long> metrics = Maps.newHashMap();
		getSessionMetrics(mbs, metrics);

		if (metrics.size() > 0) {
			recordGaugeValues(metrics);
		}
	}

	private void getSessionMetrics(MBeanServer mBeanServer, Map<String, Long> metrics) {
		try {
			for (String sessionBean1 : sessionBeans) {
				ObjectName beanName = new ObjectName(sessionBean1);
				for (String sessionAtt : sessionAtts) {
					Object sessionValue = ManagementFactory.getPlatformMBeanServer()
							.getAttribute(beanName, sessionAtt);
					String path = sessionBean1.substring(sessionBean1.indexOf('/') + 1,
							sessionBean1.indexOf(",host"));
					Long value = new Long(sessionValue.toString());
					if (value > 0) {
						metrics.put("session." + path + TagUtil.TAG_SEPARATOR_SB + sessionAtt,
								new Long(sessionValue.toString()));
					}
				}
			}

		} catch (Exception e) {
			System.out.println(e.toString());
		}
	}

}
