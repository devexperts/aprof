/*
 *  Aprof - Java Memory Allocation Profiler
 *  Copyright (C) 2002-2012  Devexperts LLC
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.devexperts.aproftest;

import com.devexperts.aprof.*;

import java.io.*;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.StringTokenizer;

/**
 * @author Dmitry Paraschenko
 */
public class TestSuite {
	private static final List<TestCase> TEST_CASES = Arrays.<TestCase>asList(
			new GenericTest(),
			new NewTest(),
			new BoxingTest(),
			new ReflectionTest(),
			new CloneTest()
	);

	public static Collection<TestCase> getTestCases() {
		return TEST_CASES;
	}

	public static void testSingleCase(TestCase test) {
		System.out.printf("Testing %s on test '%s'\n", Version.compact(), test.name());

		Snapshot snapshot = new Snapshot();
		// clearing STATISTICS
		AProfRegistry.makeSnapshot(new Snapshot());
		for (int i = 0; i < 5; i++) {
			test.doTest();
			if (i == 0) {
				// retrieving STATISTICS
				AProfRegistry.makeSnapshot(snapshot);
			}
		}
		String[] prefixes = test.getCheckedClasses();
		if (prefixes == null) {
			prefixes = new String[] {test.getClass().getCanonicalName() + "$"};
		}
		
		for (int i = 0; i < snapshot.getUsed(); i++) {
			Snapshot child = snapshot.getItem(i);
			boolean tracked = false;
			for (String prefix : prefixes) {
				if (child.getId().startsWith(prefix)) {
					tracked = true;
					break;
				}
			}
			if (!tracked) {
				snapshot.sub(child);
				child.clearDeep();
			}
		}

		compact(snapshot);

		Configuration configuration = AProfRegistry.getConfiguration();
		if (configuration == null) {
			System.out.println("Tests should be run under Aprof: -javaagent:aprof.jar");
			return;
		}

		String reason = test.verifyConfiguration(configuration);
		if (reason != null) {
			System.out.printf("Test '%s' should be run with options: %s\n", test.name(), reason);
			return;
		}

		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		PrintWriter out = new PrintWriter(bos);
		new DumpFormatter(configuration).dumpSection(out, snapshot, 0);
		out.flush();
		String received = new String(bos.toByteArray());

		if (!compareStatistics(received, test.getExpectedStatistics())) {
			System.err.printf("Test '%s' failed. Collected allocations:\n", test.name());
			System.err.println(received);
			System.out.printf("Test '%s' failed. Collected allocations were printed to stderr\n", test.name());
		} else {
			System.out.printf("Test '%s' passed\n", test.name());
		}
	}

	private static Snapshot compact(Snapshot snapshot) {
		Snapshot result = new Snapshot(snapshot.getId(), snapshot.getCounts().length);
		for (int i = 0; i < snapshot.getUsed(); i++) {
			Snapshot child = snapshot.getItem(i);
			if (AProfRegistry.isInternalLocation(child.getId())) {
				result.add(child);
				child.clearDeep();
			} else {
				result.add(compact(child));
			}
		}
		snapshot.sub(result);
		return result;
	}

	public static boolean compareStatistics(String received, String expected) {
		if (expected == null)
			return true;
		StringTokenizer out = new StringTokenizer(received, "\n\r");
		StringTokenizer ans = new StringTokenizer(expected, "\n\r");
		while (out.hasMoreTokens() && ans.hasMoreTokens()) {
			String outToken = out.nextToken();
			String ansToken = ans.nextToken();
			if (!outToken.equals(ansToken)) {
				return false;
			}
		}
		return out.hasMoreTokens() == ans.hasMoreTokens();
	}
}
