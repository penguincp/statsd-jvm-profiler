package com.etsy.statsd.profiler.profilers;

import java.util.Map;

public interface Algorithm {
	public Long doAlgorithm(Map<String, Long> metrics, String metricKey, Long newAttriValue);
}
