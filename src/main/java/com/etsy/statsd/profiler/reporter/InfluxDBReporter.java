package com.etsy.statsd.profiler.reporter;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.influxdb.InfluxDB;
import org.influxdb.InfluxDBFactory;
import org.influxdb.dto.BatchPoints;
import org.influxdb.dto.Point;

import com.etsy.statsd.profiler.Arguments;
import com.etsy.statsd.profiler.util.TagUtil;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;

/**
 * Reporter that sends data to InfluxDB
 *
 * @author Andrew Johnson
 */
public class InfluxDBReporter extends Reporter<InfluxDB> {
	public static final String VALUE_COLUMN = "value";
	public static final String USERNAME_ARG = "username";
	public static final String PASSWORD_ARG = "password";
	public static final String DATABASE_ARG = "database";
	public static final String TAG_MAPPING_ARG = "tagMapping";

	private String username;
	private String password;
	private String database;
	private String tagMapping;
	private final Map<String, String> tags;

	public InfluxDBReporter(Arguments arguments) {
		super(arguments);
		String prefix = arguments.metricsPrefix;
		// If we have a tag mapping it must match the number of components of the prefix
		Preconditions.checkArgument(
				tagMapping == null || tagMapping.split("\\.").length == prefix.split("\\.").length);
		tags = TagUtil.getTags(tagMapping, prefix, true);
	}

	/**
	 * Record a gauge value in InfluxDB
	 *
	 * @param key The key for the gauge
	 * @param value The value of the gauge
	 */
	@Override
	public void recordGaugeValue(String key, long value) {
		Map<String, Long> gauges = ImmutableMap.of(key, value);
		recordGaugeValues(gauges);
	}

	/**
	 * @see #recordGaugeValue(String, long)
	 */
	@Override
	public void recordGaugeValue(String key, double value) {
		Map<String, ? extends Number> gauges = ImmutableMap.of(key, value);
		recordGaugeValues(gauges);
	}

	/**
	 * Record multiple gauge values in InfluxDB
	 *
	 * @param gauges A map of gauge names to values
	 */
	@Override
	public void recordGaugeValues(Map<String, ? extends Number> gauges) {
		long time = System.currentTimeMillis();
		BatchPoints batchPoints = BatchPoints.database(database).build();
		for (Map.Entry<String, ? extends Number> gauge : gauges.entrySet()) {
			batchPoints.point(constructPoint(time, gauge.getKey(), gauge.getValue()));
		}
		client.write(batchPoints);
	}

	/**
	 * InfluxDB has a rich query language and does not need the bounds metrics emitted by CPUTracingProfiler
	 * As such we can disable emitting these metrics
	 *
	 * @return false
	 */
	@Override
	public boolean emitBounds() {
		return false;
	}

	/**
	 *
	 * @param server The server to which to report data
	 * @param port The port on which the server is running
	 * @param prefix The prefix for metrics
	 * @return An InfluxDB client
	 */
	@Override
	protected InfluxDB createClient(String server, int port, String prefix) {
		return InfluxDBFactory.connect(String.format("http://%s:%d", server, port), username,
				password);
	}

	/**
	 * Handle remaining arguments
	 *
	 * @param arguments The arguments given to the profiler agent
	 */
	@Override
	protected void handleArguments(Arguments arguments) {
		username = arguments.getStringArgument(USERNAME_ARG);
		password = arguments.getStringArgument(PASSWORD_ARG);
		database = arguments.getStringArgument(DATABASE_ARG);
		tagMapping = arguments.getStringArgument(TAG_MAPPING_ARG);

		Preconditions.checkNotNull(username);
		Preconditions.checkNotNull(password);
		Preconditions.checkNotNull(database);
	}

	private Point constructPoint(long time, String key, Number value) {
		Point.Builder builder = Point.measurement(key).time(time, TimeUnit.MILLISECONDS)
				.field(VALUE_COLUMN, value);
		for (Map.Entry<String, String> entry : tags.entrySet()) {
			builder = builder.tag(entry.getKey(), entry.getValue());
		}

		return builder.build();
	}
}
