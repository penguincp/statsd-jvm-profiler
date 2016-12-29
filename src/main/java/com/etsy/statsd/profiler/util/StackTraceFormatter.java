package com.etsy.statsd.profiler.util;

import java.util.ArrayList;
import java.util.List;

import com.google.common.base.Joiner;

/**
 * Utility class for formatting stack traces
 *
 * @author Andrew Johnson
 */
public final class StackTraceFormatter {
	private StackTraceFormatter() {
	}

	/**
	 * Formats a StackTraceElement as a String, excluding the line number
	 *
	 * @param element The StackTraceElement to format
	 * @return A String representing the given StackTraceElement
	 */
	public static String formatStackTraceElement(StackTraceElement element) {
		return String.format("%s-%s-%d", element.getClassName().replace(".", "-"),
				element.getMethodName(), element.getLineNumber());
	}

	/**
	 * Formats an entire stack trace as a String
	 *
	 * @param stack The stack trace to format
	 * @return A String representing the given stack trace
	 */
	public static String formatStackTrace(StackTraceElement[] stack) {
		List<String> lines = new ArrayList<>();
		//		lines.add("cpu.trace");
		//		lines.add("trace");
		for (StackTraceElement element : stack) {
			lines.add(formatStackTraceElement(element));
		}

		return Joiner.on("|").join(lines);
	}
}
