package com.etsy.statsd.profiler;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import com.etsy.statsd.profiler.profilers.CPUTracingProfiler;
import com.etsy.statsd.profiler.profilers.MemoryProfiler;
import com.etsy.statsd.profiler.reporter.StatsDReporter;

public class ArgumentsTest {
	@Rule
	public ExpectedException exception = ExpectedException.none();

	@Test
	public void testConfFileArgument() {
		String args = "conf=com/etsy/statsd/profiler/configTest.conf";
		//		String args = "conf=C:\\devops\\explore\\statsd-jvm-profiler\\src\\test\\java\\com\\etsy\\statsd\\profiler\\configTest.conf,tomcat.sessionAttributes=attr3:attr4,server=localhost,port=8125";

		Arguments arguments = Arguments.parseArgs(args);
		assertEquals("localhost", arguments.server);
		assertEquals(8125, arguments.port);
		List<Map> beans = arguments.getListArguments("beans");

		assertEquals("bean0_name", "jboss.web:type=Manager,context=/a,host=localhost",
				beans.get(0).get("name"));
		assertEquals("bean0_metric", "session_a", beans.get(0).get("metric"));
		assertArrayEquals(new String[] { "activeSessions", "expiredSessions" },
				((List) (beans.get(0).get("attributes"))).toArray(new String[0]));

		assertEquals("bean0_name",
				"jboss.web:type=DataSource,class=javax.sql.DataSource,name=\"b\"",
				beans.get(1).get("name"));
	}

	@Test
	public void testInvalidArgument() {
		String args = "key=value,key2";

		exception.expect(IllegalArgumentException.class);
		Arguments.parseArgs(args);
	}

	@Test
	public void testMissingRequiredArgument() {
		String args = "server=localhost,prefix=prefix";

		exception.expect(IllegalArgumentException.class);
		Arguments.parseArgs(args);
	}

	@Test
	public void testNonNumericPort() {
		String args = "server=localhost,port=abcd";

		exception.expect(NumberFormatException.class);
		Arguments.parseArgs(args);
	}

	@Test
	public void testNoOptionalArguments() {
		String args = "server=localhost,port=8125";
		Arguments arguments = Arguments.parseArgs(args);

		assertEquals("localhost", arguments.server);
		assertEquals(8125, arguments.port);
		assertEquals("statsd-jvm-profiler", arguments.metricsPrefix);
	}

	@Test
	public void testOptionalArguments() {
		String args = "server=localhost,port=8125,prefix=i.am.a.prefix,packageWhitelist=com.etsy";
		Arguments arguments = Arguments.parseArgs(args);

		assertEquals("localhost", arguments.server);
		assertEquals(8125, arguments.port);
		assertEquals("i.am.a.prefix", arguments.metricsPrefix);
	}

	@Test
	public void testDefaultProfilers() {
		String args = "server=localhost,port=8125";
		Arguments arguments = Arguments.parseArgs(args);

		Set<Class<? extends Profiler>> expected = new HashSet<>();
		expected.add(CPUTracingProfiler.class);
		expected.add(MemoryProfiler.class);

		assertEquals(expected, arguments.profilers);
	}

	@Test
	public void testProfilerWithPackage() {
		String args = "server=localhost,port=8125,profilers=com.etsy.statsd.profiler.profilers.CPUTracingProfiler";
		Arguments arguments = Arguments.parseArgs(args);

		Set<Class<? extends Profiler>> expected = new HashSet<>();
		expected.add(CPUTracingProfiler.class);

		assertEquals(expected, arguments.profilers);
	}

	@Test
	public void testProfilerWithoutPackage() {
		String args = "server=localhost,port=8125,profilers=MemoryProfiler";
		Arguments arguments = Arguments.parseArgs(args);

		Set<Class<? extends Profiler>> expected = new HashSet<>();
		expected.add(MemoryProfiler.class);

		assertEquals(expected, arguments.profilers);
	}

	@Test
	public void testMultipleProfilers() {
		String args = "server=localhost,port=8125,profilers=CPUTracingProfiler:MemoryProfiler";
		Arguments arguments = Arguments.parseArgs(args);

		Set<Class<? extends Profiler>> expected = new HashSet<>();
		expected.add(CPUTracingProfiler.class);
		expected.add(MemoryProfiler.class);

		assertEquals(expected, arguments.profilers);
	}

	@Test
	public void testProfilerNotFound() {
		String args = "server=localhost,port=8125,profilers=FakeProfiler";

		exception.expect(IllegalArgumentException.class);
		Arguments.parseArgs(args);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testReporterNotFound() {
		String args = "server=localhost,port=8125,reporter=NotRealReporter";
		Arguments.parseArgs(args);
	}

	@Test
	public void testDefaultReporter() {
		String args = "server=localhost,port=8125";
		Arguments arguments = Arguments.parseArgs(args);

		assertEquals(StatsDReporter.class, arguments.reporter);
	}

	@Test
	public void testHttpServerEnabledByDefault() throws Exception {
		String args = "server=localhost,port=8125";
		Arguments arguments = Arguments.parseArgs(args);

		assertTrue(arguments.httpServerEnabled);
	}

	@Test
	public void testHttpServerDisabled() throws Exception {
		String args = "server=localhost,port=8125,httpServerEnabled=false";
		Arguments arguments = Arguments.parseArgs(args);

		assertFalse(arguments.httpServerEnabled);
	}
}
