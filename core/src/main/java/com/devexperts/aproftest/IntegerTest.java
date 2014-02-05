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

import com.devexperts.aprof.AProfSizeUtil;
import com.devexperts.aprof.Configuration;

import static com.devexperts.aproftest.TestUtil.fmt;

/**
 * @author Dmitry Paraschenko
 */
class IntegerTest implements TestCase {
	private static final int COUNT = 1000000;

	public String name() {
		return "integer";
	}

	public String verifyConfiguration(Configuration configuration) {
		return null;
	}

	public String[] getCheckedClasses() {
		return new String[] {Integer.class.getName()};
	}

	public String getExpectedStatistics() {
		long objSize = AProfSizeUtil.getObjectSize(new Integer(0));
		return fmt(
			"java.lang.Integer: {size} bytes in {count} objects (avg size {objSize} bytes)\n" +
			"\tjava.lang.Integer.valueOf: {size} bytes in {count} objects\n" +
			"\t\t{class}.doTest: {size} bytes in {count} objects\n",
			"class=" + getClass().getName(),
			"size=" + fmt(objSize * COUNT),
			"count=" + fmt(COUNT),
			"objSize=" + objSize);
	}

	public void doTest() {
		for (int i = 0; i < COUNT; i++) {
			if (i % 2 == 0)
				Integer.valueOf(i + 10000);
			else
				Integer.valueOf(Integer.toString(i + 10000));
		}
	}

}
