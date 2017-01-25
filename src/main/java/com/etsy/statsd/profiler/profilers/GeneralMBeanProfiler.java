package com.etsy.statsd.profiler.profilers;

import java.lang.management.ManagementFactory;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import com.etsy.statsd.profiler.Arguments;
import com.etsy.statsd.profiler.Profiler;
import com.etsy.statsd.profiler.reporter.Reporter;
import com.etsy.statsd.profiler.util.TagUtil;
import com.google.common.collect.Maps;

public class GeneralMBeanProfiler extends Profiler {

	private MBeanAttr[] beans;

	static class MBeanAttr {
		String name;
		String metricName;
		String[] attributes = new String[0];
	}

	private int period = 10;

	public GeneralMBeanProfiler(Reporter reporter, Arguments arguments) {
		super(reporter, arguments);

		//don't place the following inside handleArguments(), which will be called from within the superclass' constructor, 
		//super constructor is first called, then this class' field initialization is done.
		//if the following is placed inside handleArguments(), sessionBeans will be reset to new String[]{}
		List<Map> beanArgs = arguments.getListArguments("beans");
		beans = new MBeanAttr[beanArgs.size()];
		for (int i = 0; i < beanArgs.size(); i++) {
			Map beanArg = beanArgs.get(i);
			MBeanAttr bean = new MBeanAttr();
			bean.name = (String) beanArg.get("name");
			bean.metricName = (String) beanArg.get("metric");
			bean.attributes = (String[]) ((List) beanArg.get("attributes")).toArray(new String[0]);

			beans[i] = bean;
		}

		this.period = arguments.getIntArgument("GeneralMBeanProfiler-period");
		if (this.period == -1) {
			this.period = arguments.getIntArgument("period");
		}
		if (this.period == -1) {
			this.period = 10;
		}

	}

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
		return this.period;
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
		getMBeanMetrics(mbs, metrics);

		if (metrics.size() > 0) {
			recordGaugeValues(metrics);
		}
	}

	private void getMBeanMetrics(MBeanServer mBeanServer, Map<String, Long> metrics) {
		for (MBeanAttr bean : beans) {
			try {
				ObjectName beanName = new ObjectName(bean.name);
				for (String attr : bean.attributes) {

					Object attrValue = ManagementFactory.getPlatformMBeanServer()
							.getAttribute(beanName, attr);

					Long value = new Long(attrValue.toString());
					if (value > 0) {
						metrics.put(bean.metricName + TagUtil.TAG_SEPARATOR_SB + attr,
								new Long(attrValue.toString()));
					}
				}
			} catch (Exception e) {
				System.out.println(e.toString());
				//			e.printStackTrace();
			}
		}

	}
}
