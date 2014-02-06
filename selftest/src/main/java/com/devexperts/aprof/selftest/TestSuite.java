/*
 * Aprof - Java Memory Allocation Profiler
 * Copyright (C) 2002-2014  Devexperts LLC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.devexperts.aprof.selftest;

import java.io.*;
import java.util.*;

import com.devexperts.aprof.*;
import com.devexperts.aprof.dump.*;
import com.devexperts.aprof.util.FastOutputStreamWriter;

/**
 * @author Dmitry Paraschenko
 */
public class TestSuite {
	private static final int TEST_RUNS = 3;

	private static final List<TestCase> TEST_CASES = Arrays.asList(
			new NewTest(),
			new TrackingTest(),
			new TrackingDeepTest(),
			new TrackingIntfTest(),
			new DoubleTest(),
			new IntegerTest(),
			new StringTest(),
			new ReflectionTest(),
			new CloneTest(),
			new TryTest(),
			new DeserializationTest(),
			new ArrayNewInstanceTest(),
			new ArraySizeTest(),
			new ObjectArrayCopyTest()
	);

	public static void main(String[] args) throws IOException {
		if (args.length < 2) {
			helpSelfTest();
			return;
		}
		boolean ok = true;
		for (int i = 1; i < args.length; i++) {
			String testName = args[i].trim();
			if ("all".equalsIgnoreCase(testName)) {
				if (!TestSuite.testAllApplicableCases())
					ok = false;
				continue;
			}
			boolean done = false;
			for (TestCase test : TEST_CASES) {
				if (test.name().equalsIgnoreCase(testName)) {
					if (!TestSuite.testSingleCase(test))
						ok = false;
					done = true;
				}
			}
			if (!done) {
				helpSelfTest();
				return;
			}
		}
		if (ok) {
			System.out.println("==== ALL TESTS HAVE PASSED");
			System.exit(0);
		} else {
			System.out.println("==== SOME TESTS HAVE FAILED !!!");
			System.exit(1);
		}
	}

	private static void helpSelfTest() throws IOException {
		PrintWriter out = new PrintWriter(new FastOutputStreamWriter(System.out), true);
		out.println(AProfTools.STARTUP_NOTICE);
		out.println();
		out.println("Usage: java -ea -javaagent:aprof.jar -jar aprof.jar selftest [<test> ...]");
		out.println("Where <test> is 'all' or one of tests:");
		for (TestCase test : TEST_CASES)
			out.println("\t" + test.name());
	}

	private static boolean testAllApplicableCases() {
		AProfAgent agent = checkAgent();
		if (agent == null)
			return false;
		boolean ok = true;
		for (TestCase test : TEST_CASES) {
			if (test.verifyConfiguration(agent.getConfig()) == null) {
				if (!testSingleCase(test))
					ok = false;
			}
		}
		return ok;
	}

	private static boolean testSingleCase(TestCase test) {
		AProfAgent agent = checkAgent();
		if (agent == null)
			return false;

		System.out.printf("==== Testing %s on test '%s'%n", Version.compact(), test.name());

		Configuration config = agent.getConfig();
		String reason = test.verifyConfiguration(config);
		if (reason != null) {
			System.out.printf("Test '%s' should be run with options: %s%n", test.name(), reason);
			return false;
		}

		SnapshotRoot snapshot0 = new SnapshotRoot();
		SnapshotRoot snapshot1 = new SnapshotRoot();
		for (int i = 0; i < TEST_RUNS; i++) {
			long time = System.currentTimeMillis();
			if (i == TEST_RUNS - 1) // STATISTICS before test
				agent.getDumper().copyTotalSnapshotTo(snapshot0);
			if (!doTestOnce(test))
				return false;
			if (i == TEST_RUNS - 1) // STATISTICS after test
				agent.getDumper().copyTotalSnapshotTo(snapshot1);
			System.out.printf("Test took %d ms\n", System.currentTimeMillis() - time);
		}

		String received = snapshotToString(config, getCheckedClassesSnapshot(test, snapshot0, snapshot1));
		String expected = test.getExpectedStatistics();
		String result = compareStatistics(received, expected);

		if (result != null) {
			// Print full details to stderr
			System.err.printf("-- Test '%s' expected allocations:%n", test.name());
			System.err.println(expected);
			System.err.printf("-- Test '%s' Collected allocations:%n", test.name());
			System.err.println(received);
			// Print short summary to stdout
			System.out.println(result);
			System.out.printf("-- Test '%s' FAILED !!!%n", test.name());
			return false;
		} else {
			System.out.printf("-- Test '%s' PASSED%n", test.name());
			return true;
		}
	}

	private static SnapshotRoot getCheckedClassesSnapshot(TestCase test, SnapshotRoot snapshot0, SnapshotRoot snapshot1) {
		snapshot0.sortChildrenDeep(SnapshotShallow.COMPARATOR_NAME);
		SnapshotRoot result = new SnapshotRoot();
		String[] prefixes = test.getCheckedClasses();
		if (prefixes != null) {
			for (int i = 0; i < snapshot1.getUsed(); i++) {
				SnapshotDeep child = snapshot1.getChild(i);
				boolean tracked = false;
				for (String prefix : prefixes) {
					if (child.getName().startsWith(prefix)) {
						tracked = true;
						break;
					}
				}
				if (!tracked)
					continue;
				// add delta to result
				SnapshotDeep child0 = snapshot0.getOrCreateChild(child.getName(), child.isArray(), child.getHistoCountsLength());
				SnapshotDeep resultChild = result.getOrCreateChild(child.getName(), child.isArray(), child.getHistoCountsLength());
				resultChild.addDeep(child);
				resultChild.subDeep(child0);
			}
		}
		return result;
	}

	private static String snapshotToString(Configuration config, SnapshotRoot snapshot) {
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		PrintWriter out = new PrintWriter(bos);
		snapshot.sortChildrenDeep(SnapshotShallow.COMPARATOR_NAME);
		new DumpFormatter(config).dumpSnapshotByDataTypes(out, snapshot);
		out.flush();
		return new String(bos.toByteArray());
	}

	private static boolean doTestOnce(TestCase test) {
		try {
			test.doTest();
		} catch (Throwable t) {
			System.out.printf("-- Test '%s' FAILED with exception !!!%n", test.name());
			t.printStackTrace(System.out);
			return false;
		}
		return true;
	}

	private static String compareStatistics(String received, String expected) {
		if (expected == null)
			return null;
		StringTokenizer out = new StringTokenizer(received, "\n\r");
		StringTokenizer ans = new StringTokenizer(expected, "\n\r");
		int lineNo = 0;
		while (out.hasMoreTokens() && ans.hasMoreTokens()) {
			lineNo++;
			String outToken = out.nextToken();
			String ansToken = ans.nextToken();
			if (!ansToken.equals(outToken))
				return String.format("Line %d does not match. Expected vs collected:%n%s%n%s", lineNo, ansToken, outToken);
		}
		lineNo++;
		if (out.hasMoreTokens())
			return String.format("Extra line %d. Collected:%n%s", lineNo, out.nextToken());
		if (ans.hasMoreTokens())
			return String.format("Missing line %d. Expected:%n%s", lineNo, ans.nextToken());
		return null;
	}

	private static AProfAgent checkAgent() {
		AProfAgent agent = AProfAgent.getInstance();
		if (agent == null)
			System.out.println("Tests should be run under Aprof: -javaagent:aprof.jar");
		return agent;
	}
}
