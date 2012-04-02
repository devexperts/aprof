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

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
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
			new ReflectionTest(),
			new CloneTest()
	);

	public static Collection<TestCase> getTestCases() {
		return TEST_CASES;
	}

	public static void testSingleCase(TestCase test) {
		System.out.printf("Testing %s on test case '%s'\n", Version.compact(), test.name());

		for (int i = 0; i < 5; i++) {
			// clearing STATISTICS
			AProfRegistry.makeSnapshot(new Snapshot());
			test.doTest();
		}

		// retrieving STATISTICS
		Snapshot snapshot = new Snapshot();
		AProfRegistry.makeSnapshot(snapshot);

		for (int i = 0; i < snapshot.getUsed(); i++) {
			Snapshot child = snapshot.getItem(i);
			if (!child.getId().startsWith(test.getClass().getCanonicalName() + "$")) {
				snapshot.sub(child);
				child.clearDeep();
			}
		}

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
