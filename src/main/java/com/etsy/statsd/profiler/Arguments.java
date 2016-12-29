package com.etsy.statsd.profiler;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import com.etsy.statsd.profiler.profilers.MemoryProfiler;
import com.etsy.statsd.profiler.reporter.Reporter;
import com.etsy.statsd.profiler.reporter.StatsDReporter;
import com.google.common.base.Optional;
import com.google.common.collect.Lists;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValue;

/**
 * Represents arguments to the profiler
 *
 * @author Andrew Johnson
 */
public final class Arguments {
	private static final String SERVER = "server";
	private static final String PORT = "port";
	private static final String METRICS_PREFIX = "prefix";
	private static final String PROFILERS = "profilers";
	private static final String REPORTER = "reporter";
	private static final String HTTP_PORT = "httpPort";
	private static final String HTTP_SEVER_ENABLED = "httpServerEnabled";

	private static final Collection<String> REQUIRED = Arrays.asList(SERVER, PORT);

	public String server;
	public int port;
	public String metricsPrefix;
	public Set<Class<? extends Profiler>> profilers;
	public Map<String, Object> parsedArgs;
	public Class<? extends Reporter<?>> reporter;
	public int httpPort;
	public boolean httpServerEnabled;

	private Arguments(Map<String, Object> parsedArgs) {
		this.parsedArgs = parsedArgs;

		this.handleConfFileArgument();

		server = this.getStringArgument(SERVER);
		port = this.getIntArgument(PORT);
		metricsPrefix = Optional.fromNullable(this.getStringArgument(METRICS_PREFIX))
				.or("statsd-jvm-profiler");
		profilers = parseProfilerArg(this.getStringArgument(PROFILERS));
		reporter = parserReporterArg(this.getStringArgument(REPORTER));
		httpPort = Integer
				.parseInt(Optional.fromNullable(this.getStringArgument(HTTP_PORT)).or("5005"));
		httpServerEnabled = Boolean.parseBoolean(
				Optional.fromNullable(this.getStringArgument(HTTP_SEVER_ENABLED)).or("true"));

		for (String requiredArg : REQUIRED) {
			if (!this.parsedArgs.containsKey(requiredArg)) {
				throw new IllegalArgumentException(
						String.format("%s argument was not supplied", requiredArg));
			}
		}
	}

	private void handleConfFileArgument() {

		String confFile = this.getStringArgument("conf");
		if (confFile != null) {

			Config config = null;
			if (new File(confFile).exists()) {
				config = ConfigFactory.parseFile(new File(confFile));
			} else {
				config = ConfigFactory.load(confFile);
			}
			Set<Entry<String, ConfigValue>> entries = config.entrySet();
			for (Entry<String, ConfigValue> entry : entries) {
				//arguments defined in the commandline will supercede arguments defined in the conf file 
				if (!this.parsedArgs.containsKey(entry.getKey())) {
					this.parsedArgs.put(entry.getKey(), entry.getValue().unwrapped());
				}
			}
		}
	}

	public void mergeArguments(Map newArgs) {
		if (newArgs != null && newArgs.size() > 0) {
			this.parsedArgs.putAll(newArgs);
		}
	}

	public String getStringArgument(String key) {
		Object value = parsedArgs.get(key);
		if (value != null) {
			return value.toString();
		}
		return null;
	}

	public int getIntArgument(String key) {
		Object value = parsedArgs.get(key);
		if (value != null) {
			return Integer.parseInt(value.toString());
		}
		return -1;
	}

	public List<Map> getListArguments(String key) {
		Object value = parsedArgs.get(key);
		if (value != null) {
			return (List<Map>) value;
		}

		return Lists.newArrayList();
	}

	public String[] getStringListArguments(String key) {
		Object value = parsedArgs.get(key);
		if (value != null) {
			if (value instanceof List) {
				return (String[]) ((List) value).toArray(new String[0]);
			}

			if (value.getClass().isArray()) {
				Object[] objValues = (Object[]) value;
				return Arrays.asList(objValues).toArray(new String[objValues.length]);
			}
			String valueStr = value.toString();
			if (valueStr.contains(":")) {
				return valueStr.split(":");
			}
			return new String[] { valueStr };

		}

		return new String[] {};
	}

	/**
	 * Parses arguments into an Arguments object
	 *
	 * @param args A String containing comma-delimited args in k=v form
	 * @return An Arguments object representing the given arguments
	 */
	public static Arguments parseArgs(final String args) {
		Map<String, Object> parsed = new HashMap<>();
		for (String argPair : args.split(",")) {
			String[] tokens = argPair.split("=");
			if (tokens.length != 2) {
				throw new IllegalArgumentException(
						"statsd-jvm-profiler takes a comma-delimited list of arguments in k=v form");
			}

			parsed.put(tokens[0], tokens[1]);
		}

		return new Arguments(parsed);
	}

	@SuppressWarnings("unchecked")
	private static Class<? extends Reporter<?>> parserReporterArg(String reporterArg) {
		if (reporterArg == null) {
			return StatsDReporter.class;
		} else {
			try {
				return (Class<? extends Reporter<?>>) Class.forName(reporterArg);
			} catch (ClassNotFoundException e) {
				// This might indicate the package was left off, so we'll try with the default package
				try {
					return (Class<? extends Reporter<?>>) Class
							.forName("com.etsy.statsd.profiler.reporter." + reporterArg);
				} catch (ClassNotFoundException inner) {
					throw new IllegalArgumentException("Reporter " + reporterArg + " not found",
							inner);
				}
			}
		}
	}

	@SuppressWarnings("unchecked")
	private static Set<Class<? extends Profiler>> parseProfilerArg(String profilerArg) {
		Set<Class<? extends Profiler>> parsedProfilers = new HashSet<>();
		if (profilerArg == null) {
			parsedProfilers.add(MemoryProfiler.class);
		} else {
			for (String p : profilerArg.split(":")) {
				try {
					parsedProfilers.add((Class<? extends Profiler>) Class
							.forName("com.etsy.statsd.profiler.profilers." + p));
				} catch (ClassNotFoundException e) {
					// This might indicate the package was left off, so we'll try with the default package
					try {
						parsedProfilers.add((Class<? extends Profiler>) Class.forName(p));
					} catch (ClassNotFoundException inner) {
						throw new IllegalArgumentException("Profiler " + p + " not found", inner);
					}
				}
			}
		}

		if (parsedProfilers.isEmpty()) {
			throw new IllegalArgumentException("At least one profiler must be specified");
		}

		return parsedProfilers;
	}
}
