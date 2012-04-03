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

import com.devexperts.aprof.Configuration;

/**
 * @author Dmitry Paraschenko
 */
class BoxingTest implements TestCase {
	public String name() {
		return "boxing";
	}

	public String verifyConfiguration(Configuration configuration) {
		return null;
	}

	public String[] getCheckedClasses() {
		return new String[] {Double.class.getCanonicalName()};
	}

	public String getExpectedStatistics() {
		return STATISTICS;
	}

	public void doTest() {
		long time = System.currentTimeMillis();
		for (int i = 0; i < 1000000; i++) {
			if (i % 100000 == 0)
				System.out.print('.');
			Double.valueOf(10000 + i);
		}
		System.out.printf(" Test took %d ms\n", System.currentTimeMillis() - time);
	}

	private static String STATISTICS = "" +
			"Allocated 16,000,000 bytes in 1,000,000 objects in 1 locations of 1 classes\n" +
			"-------------------------------------------------------------------------------\n" +
			"java.lang.Double: 16,000,000 (100%) bytes in 1,000,000 (100%) objects (avg size 16 bytes)\n" +
			"\tjava.lang.Double.valueOf: 16,000,000 (100%) bytes in 1,000,000 (100%) objects\n" +
			"\t\tjava.lang.Double.valueOf: 16,000,000 (100%) bytes in 1,000,000 (100%) objects\n" +
			"\t\t\tcom.devexperts.aproftest.BoxingTest.doTest: 16,000,000 (100%) bytes in 1,000,000 (100%) objects\n" +
			"";
}
