package com.etsy.statsd.profiler.profilers;

import java.util.Map;

public class DiffAlgorithm implements Algorithm {

	@Override
	public Long doAlgorithm(Map<String, Long> metrics, String metricKey, Long newAttriValue) {
		Long existingValue = metrics.get(metricKey);
		Long ret = newAttriValue - existingValue;
		metrics.put(metricKey, newAttriValue);

		return ret;

	}
}
