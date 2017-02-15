package com.etsy.statsd.profiler;

import java.lang.instrument.Instrumentation;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.etsy.statsd.profiler.reporter.Reporter;
import com.etsy.statsd.profiler.server.ProfilerServer;
import com.etsy.statsd.profiler.worker.ProfilerShutdownHookWorker;
import com.etsy.statsd.profiler.worker.ProfilerThreadFactory;
import com.etsy.statsd.profiler.worker.ProfilerWorkerThread;
import com.google.common.util.concurrent.MoreExecutors;

/**
 * javaagent profiler using StatsD as a backend
 *
 * @author Andrew Johnson
 */
public final class Agent {
	public static final int EXECUTOR_DELAY = 0;

	static AtomicReference<Boolean> isRunning = new AtomicReference<>(true);
	static LinkedList<String> errors = new LinkedList<>();

	private static Arguments arguments;
	private static Collection<Profiler> profilers;
	private static ScheduledExecutorService scheduledExecutorService;
	private static Reporter reporter;

	private static Map<String, ScheduledFuture<?>> runningProfilers;

	private Agent() {
	}

	public static void agentmain(final String args, final Instrumentation instrumentation) {
		premain(args, instrumentation);
	}

	/**
	 * Start the profiler
	 *
	 * @param args Profiler arguments
	 * @param instrumentation Instrumentation agent
	 */
	public static void premain(final String args, final Instrumentation instrumentation) {
		Arguments arguments = Arguments.parseArgs(args);
		Agent.arguments = arguments;
		reporter = instantiate(arguments.reporter, Reporter.CONSTRUCTOR_PARAM_TYPES, arguments);

		profilers = new ArrayList<>();
		for (Class<? extends Profiler> profiler : arguments.profilers) {
			profilers.add(
					instantiate(profiler, Profiler.CONSTRUCTOR_PARAM_TYPES, reporter, arguments));
		}

		scheduleProfilers(profilers, arguments);
		registerShutdownHook(profilers);
	}

	/**
	 * Schedule profilers with a SchedulerExecutorService
	 *
	 * @param profilers Collection of profilers to schedule
	 * @param arguments
	 */
	private static void scheduleProfilers(Collection<Profiler> profilers, Arguments arguments) {
		// We need to convert to an ExitingScheduledExecutorService so the JVM shuts down
		// when the main thread finishes
		scheduledExecutorService = MoreExecutors
				.getExitingScheduledExecutorService((ScheduledThreadPoolExecutor) Executors
						.newScheduledThreadPool(profilers.size(), new ProfilerThreadFactory()));

		runningProfilers = new HashMap<>(profilers.size());
		Map<String, Profiler> activeProfilers = new HashMap<>(profilers.size());
		for (Profiler profiler : profilers) {
			activeProfilers.put(profiler.getClass().getSimpleName(), profiler);
			ProfilerWorkerThread worker = new ProfilerWorkerThread(profiler, errors);
			ScheduledFuture future = scheduledExecutorService.scheduleAtFixedRate(worker,
					EXECUTOR_DELAY, profiler.getPeriod(), profiler.getTimeUnit());
			runningProfilers.put(profiler.getClass().getSimpleName(), future);
		}

		if (arguments.httpServerEnabled) {
			ProfilerServer.startServer(scheduledExecutorService, runningProfilers, activeProfilers,
					new AtomicInteger(arguments.httpPort), isRunning, errors);
		}
	}

	public static void initiateAndScheduleProfiler(String profilerName, Map args) {
		Class<? extends Profiler> profilerClass;
		try {
			profilerClass = (Class<? extends Profiler>) Class
					.forName("com.etsy.statsd.profiler.profilers." + profilerName);
		} catch (ClassNotFoundException e) {
			try {
				profilerClass = (Class<? extends Profiler>) Class.forName(profilerName);
			} catch (ClassNotFoundException e2) {
				throw new IllegalArgumentException("Profiler " + profilerName + " not found");
			}
		}

		arguments.mergeArguments(args);

		Profiler profiler = instantiate(profilerClass, Profiler.CONSTRUCTOR_PARAM_TYPES, reporter,
				arguments);
		profilers.add(profiler);

		ProfilerWorkerThread worker = new ProfilerWorkerThread(profiler, errors);
		ScheduledFuture future = scheduledExecutorService.scheduleAtFixedRate(worker,
				EXECUTOR_DELAY, profiler.getPeriod(), profiler.getTimeUnit());
		runningProfilers.put(profiler.getClass().getSimpleName(), future);

	}

	/**
	 * Register a shutdown hook to flush profiler data to StatsD
	 *
	 * @param profilers The profilers to flush at shutdown
	 */
	private static void registerShutdownHook(Collection<Profiler> profilers) {
		Thread shutdownHook = new Thread(new ProfilerShutdownHookWorker(profilers, isRunning));
		Runtime.getRuntime().addShutdownHook(shutdownHook);
	}

	/**
	 * Uniformed handling of initialization exception
	 *
	 * @param clazz The class that could not be instantiated
	 * @param cause The underlying exception
	 */
	private static void handleInitializationException(final Class<?> clazz, final Exception cause) {
		throw new RuntimeException(
				"Unable to instantiate " + clazz.getSimpleName() + ":" + cause.getMessage(), cause);
	}

	/**
	 * Instantiate an object
	 *
	 * @param clazz A Class representing the type of object to instantiate
	 * @param parameterTypes The parameter types for the constructor
	 * @param initArgs The values to pass to the constProfiler server started on portructor
	 * @param <T> The type of the object to instantiate
	 * @return A new instance of type T
	 */
	public static <T> T instantiate(final Class<T> clazz, Class<?>[] parameterTypes,
			Object... initArgs) {
		try {
			Constructor<T> constructor = clazz.getConstructor(parameterTypes);
			return constructor.newInstance(initArgs);
		} catch (NoSuchMethodException | IllegalAccessException | InstantiationException
				| InvocationTargetException e) {
			handleInitializationException(clazz, e);
		}

		return null;
	}
}
