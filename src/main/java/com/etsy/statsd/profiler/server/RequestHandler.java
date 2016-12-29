package com.etsy.statsd.profiler.server;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicReference;

import org.vertx.java.core.Handler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.http.RouteMatcher;
import org.vertx.java.core.json.JsonObject;

import com.etsy.statsd.profiler.Agent;
import com.etsy.statsd.profiler.Profiler;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;

/**
 * Handler for HTTP requests to the profiler server
 *
 * @author Andrew Johnson
 */
public final class RequestHandler {
	private RequestHandler() {
	}

	/**
	 * Construct a RouteMatcher for the supported routes
	 *
	 * @param activeProfilers The active profilers
	 * @return A RouteMatcher that matches all supported routes
	 */
	public static RouteMatcher getMatcher(final ScheduledExecutorService scheduledExecutorService,
			final Map<String, ScheduledFuture<?>> runningProfilers,
			Map<String, Profiler> activeProfilers, AtomicReference<Boolean> isRunning,
			List<String> errors) {
		RouteMatcher matcher = new RouteMatcher();
		matcher.get("/profilers", RequestHandler.handleGetProfilers(runningProfilers));
		matcher.get("/disable/:profiler", RequestHandler.handleDisableProfiler(runningProfilers));
		matcher.get("/status/:profiler", RequestHandler.handleProfilerStatus(activeProfilers));
		matcher.get("/errors", RequestHandler.handleErrorMessages(errors));
		matcher.get("/isRunning", RequestHandler.isRunning(isRunning));

		matcher.post("/enable/:profiler",
				RequestHandler.handleEanbleProfiler(scheduledExecutorService, runningProfilers));
		return matcher;
	}

	private static Handler<HttpServerRequest> handleEanbleProfiler(
			final ScheduledExecutorService scheduledExecutorService,
			final Map<String, ScheduledFuture<?>> activeProfilers) {

		return new Handler<HttpServerRequest>() {
			@Override
			public void handle(final HttpServerRequest httpServerRequest) {

				httpServerRequest.bodyHandler(new Handler<Buffer>() {
					public void handle(Buffer body) {
						JsonObject json = new JsonObject(body.toString());

						Map newArgs = json.toMap();

						String profilerToEnable = httpServerRequest.params().get("profiler");
						if (activeProfilers.containsKey(profilerToEnable)) {
							httpServerRequest.response().end(String
									.format("Profiler %s is already running", profilerToEnable));
						} else {
							try {
								Agent.initiateAndScheduleProfiler(profilerToEnable, newArgs);
								httpServerRequest.response().end(
										String.format("Profiler %s is enabled", profilerToEnable));
							} catch (Exception e) {
								httpServerRequest.response()
										.end(String.format("Error in enabling %s: %s\n",
												profilerToEnable, e.getMessage()));
							}
						}
					}
				});

			}
		};
	}

	/**
	 * Handle a GET to /isRunning
	 *
	 * @return A Handler that returns all running profilers
	 */
	public static Handler<HttpServerRequest> isRunning(final AtomicReference<Boolean> isRunning) {
		return new Handler<HttpServerRequest>() {
			@Override
			public void handle(HttpServerRequest httpServerRequest) {
				httpServerRequest.response().end(String.format("isRunning: %b", isRunning.get()));
			}
		};
	}

	/**
	 * Handle a GET to /profilers
	 *
	 * @return A Handler that handles a request to the /profilers endpoint
	 */
	public static Handler<HttpServerRequest> handleGetProfilers(
			final Map<String, ScheduledFuture<?>> runningProfilers) {
		return new Handler<HttpServerRequest>() {
			@Override
			public void handle(HttpServerRequest httpServerRequest) {
				httpServerRequest.response()
						.end(Joiner.on("\n").join(getEnabledProfilers(runningProfilers)));
			}
		};
	}

	/**
	 * Handle a GET to /errors
	 *
	 * @return The last 10 error stacktraces
	 */
	public static Handler<HttpServerRequest> handleErrorMessages(final List<String> errors) {
		return new Handler<HttpServerRequest>() {
			@Override
			public void handle(HttpServerRequest httpServerRequest) {
				httpServerRequest.response().end("Errors: " + Joiner.on("\n").join(errors));
			}
		};
	}

	/**
	 * Handle a GET to /disable/:profiler
	 *
	 * @param activeProfilers The active profilers
	 * @return A Handler that handles a request to the /disable/:profiler endpoint
	 */
	public static Handler<HttpServerRequest> handleDisableProfiler(
			final Map<String, ScheduledFuture<?>> activeProfilers) {
		return new Handler<HttpServerRequest>() {
			@Override
			public void handle(HttpServerRequest httpServerRequest) {
				String profilerToDisable = httpServerRequest.params().get("profiler");
				ScheduledFuture<?> future = activeProfilers.get(profilerToDisable);
				if (future != null) {
					future.cancel(false);
					activeProfilers.remove(profilerToDisable);
					httpServerRequest.response()
							.end(String.format("Disabled profiler %s\n", profilerToDisable));
				} else {
					httpServerRequest.response().end(
							String.format("Profiler %s is already disabled\n", profilerToDisable));
				}
			}
		};
	}

	/**
	 * Handle a GET to /status/profiler/:profiler
	 *
	 * @param activeProfilers The active profilers
	 * @return A Handler that handles a request to the /disable/:profiler endpoint
	 */
	public static Handler<HttpServerRequest> handleProfilerStatus(
			final Map<String, Profiler> activeProfilers) {
		return new Handler<HttpServerRequest>() {
			@Override
			public void handle(HttpServerRequest httpServerRequest) {
				String profilerName = httpServerRequest.params().get("profiler");
				Profiler profiler = activeProfilers.get(profilerName);
				httpServerRequest.response()
						.end(String.format(profilerName + " has recorded stats %d times\n",
								profiler.getRecordedStats()));
			}
		};
	}

	/**
	 * Get all enabled profilers
	 * @param activeProfilers The active profilers
	 * @return A sorted List<String> containing the names of profilers that are currently running
	 */
	private static List<String> getEnabledProfilers(
			final Map<String, ScheduledFuture<?>> activeProfilers) {
		Collection<String> profilers = Collections2.transform(Collections2.filter(
				activeProfilers.entrySet(), new Predicate<Map.Entry<String, ScheduledFuture<?>>>() {
					@Override
					public boolean apply(Map.Entry<String, ScheduledFuture<?>> input) {
						return !input.getValue().isDone();
					}
				}), new Function<Map.Entry<String, ScheduledFuture<?>>, String>() {
					@Override
					public String apply(Map.Entry<String, ScheduledFuture<?>> input) {
						return input.getKey();
					}
				});

		List<String> result = new ArrayList<>(profilers);
		Collections.sort(result);
		return result;

	}

}
