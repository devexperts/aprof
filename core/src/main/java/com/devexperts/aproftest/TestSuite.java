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

package com.devexperts.aproftest;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.util.*;
import java.util.regex.Pattern;

import com.devexperts.aprof.*;
import com.devexperts.aprof.dump.DumpFormatter;
import com.devexperts.aprof.dump.SnapshotDeep;

/**
 * @author Dmitry Paraschenko
 */
public class TestSuite {
	private static final List<TestCase> TEST_CASES = Arrays.asList(
			new NewTest(),
			new DoubleTest(),
			new IntegerTest(),
			new ReflectionTest(),
			new CloneTest(),
			new TryTest(),
			new DeserializationTest()
	);

	public static Collection<TestCase> getTestCases() {
		return TEST_CASES;
	}

	public static boolean testAllApplicableCases() {
		Configuration configuration = checkConfiguration();
		if (configuration == null)
			return false;
		boolean ok = true;
		for (TestCase test : getTestCases()) {
			if (test.verifyConfiguration(configuration) == null) {
				if (!testSingleCase(test))
					ok = false;
			}
		}
		return ok;
	}

	public static boolean testSingleCase(TestCase test) {
		Configuration configuration = checkConfiguration();
		if (configuration == null)
			return false;

		System.out.printf("==== Testing %s on test '%s'%n", Version.compact(), test.name());

		SnapshotDeep snapshot = new SnapshotDeep();
		for (int i = 0; i < 5; i++) {
			if (i == 1) {
				// clearing STATISTICS
				AProfRegistry.takeSnapshot(new SnapshotDeep());
				if (!doTestOnce(test))
					return false;
				// retrieving STATISTICS
				AProfRegistry.takeSnapshot(snapshot);
			} else {
				if (!doTestOnce(test))
					return false;
			}
		}
		String[] prefixes = test.getCheckedClasses();
		if (prefixes != null) {
			for (int i = 0; i < snapshot.getUsed(); i++) {
				SnapshotDeep child = snapshot.getChild(i);
				boolean tracked = false;
				for (String prefix : prefixes) {
					if (child.getName().startsWith(prefix)) {
						tracked = true;
						break;
					}
				}
				if (!tracked) {
					snapshot.subShallow(child);
					child.clearDeep();
				}
			}
		}

		String reason = test.verifyConfiguration(configuration);
		if (reason != null) {
			System.out.printf("Test '%s' should be run with options: %s%n", test.name(), reason);
			return false;
		}

		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		PrintWriter out = new PrintWriter(bos);
		new DumpFormatter(configuration).dumpSection(out, snapshot, 0);
		out.flush();
		String received = new String(bos.toByteArray());

		String expected = test.getExpectedStatistics();
		String result = compareStatistics(received, expected);
		if (result != null) {
			System.out.println(result);
			System.out.println("-- Expected allocations:");
			System.out.println(expected);
			System.out.println("-- Collected allocations:");
			System.out.println(received);
			System.out.printf("-- Test '%s' FAILED !!!%n", test.name());
			return false;
		} else {
			System.out.printf("-- Test '%s' PASSED%n", test.name());
			return true;
		}
	}

	private static boolean doTestOnce(TestCase test) {
		long time = System.currentTimeMillis();
		try {
			test.doTest();
		} catch (Throwable t) {
			System.out.printf("-- Test '%s' FAILED with exception !!!%n", test.name());
			t.printStackTrace(System.out);
			return false;
		}
		System.out.printf("Test took %d ms\n", System.currentTimeMillis() - time);
		return true;
	}

	public static String compareStatistics(String received, String expected) {
		if (expected == null)
			return null;
		StringTokenizer out = new StringTokenizer(received, "\n\r");
		StringTokenizer ans = new StringTokenizer(expected, "\n\r");
		int lineNo = 0;
		while (out.hasMoreTokens() && ans.hasMoreTokens()) {
			lineNo++;
			String outToken = out.nextToken();
			String ansToken = ans.nextToken();
			if (!compile(ansToken).matcher(outToken).matches())
				return String.format("Line %d does not match. Expected vs collected:%n%s%n%s", lineNo, ansToken, outToken);
		}
		lineNo++;
		if (out.hasMoreTokens())
			return String.format("Extra line %d. Collected:%n%s", lineNo, out.nextToken());
		if (ans.hasMoreTokens())
			return String.format("Missing line %d. Expected:%n%s", lineNo, ans.nextToken());
		return null;
	}

	private static Pattern compile(String expected) {
		return Pattern.compile("\\Q" + expected.replace("_", "\\E[.,0-9]+\\Q") + "\\E");
	}

	private static Configuration checkConfiguration() {
		Configuration configuration = AProfRegistry.getConfiguration();
		if (configuration == null) {
			System.out.println("Tests should be run under Aprof: -javaagent:aprof.jar");
			return null;
		}
		return configuration;
	}
}
