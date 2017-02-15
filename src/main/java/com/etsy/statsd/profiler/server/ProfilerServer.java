package com.etsy.statsd.profiler.server;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.Handler;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.VertxFactory;
import org.vertx.java.core.http.HttpServer;

import com.etsy.statsd.profiler.Profiler;

/**
 * Sets up a simple embedded HTTP server for interacting with the profiler while it runs
 *
 * @author Andrew Johnson
 */
public final class ProfilerServer {
	private static final Logger LOGGER = Logger.getLogger(ProfilerServer.class.getName());
	private static final Vertx VERTX = VertxFactory.newVertx();

	private ProfilerServer() {
	}

	/**
	 * Start an embedded HTTP server
	 *
	 * @param activeProfilers The active profilers
	 * @param portHolder The port on which to bind the server, it is a AtomicInteger,
	 *                   because in case of port already occupied, this method will try port+1 until it succeeds, test cases need to know the port that finally succeeds
	 */
	public static void startServer(final ScheduledExecutorService scheduledExecutorService,
			final Map<String, ScheduledFuture<?>> runningProfilers,
			final Map<String, Profiler> activeProfilers, final AtomicInteger portHolder,
			final AtomicReference<Boolean> isRunning, final List<String> errors) {
		final HttpServer server = VERTX.createHttpServer();
		server.requestHandler(RequestHandler.getMatcher(scheduledExecutorService, runningProfilers,
				activeProfilers, isRunning, errors));
		final int port=portHolder.get();
		server.listen(port, new Handler<AsyncResult<HttpServer>>() {
			@Override
			public void handle(AsyncResult<HttpServer> event) {
				if (event.failed()) {
					server.close();
					portHolder.set(port+1);
					startServer(scheduledExecutorService, runningProfilers, activeProfilers,
							portHolder, isRunning, errors);
				} else if (event.succeeded()) {
					LOGGER.info("Profiler server started on port " + port);
				}
			}
		});
	}
}
