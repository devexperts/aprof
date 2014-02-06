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

import java.lang.reflect.Array;

import com.devexperts.aprof.AProfSizeUtil;
import com.devexperts.aprof.Configuration;

class ArrayNewInstanceTest implements TestCase {
	private static final int COUNT = 1000000;
	private static final int LENGTH = 5;

	public String name() {
		return "arrayNewInstance";
	}

	public String verifyConfiguration(Configuration configuration) {
		return null;
	}

	public String[] getCheckedClasses() {
		return new String[] { getClass().getName() + "$Entity[]" };
	}

	public String getExpectedStatistics() {
		long objSize = AProfSizeUtil.getObjectSize(new Entity[LENGTH]);
		return TestUtil.fmt(
			"{class}$Entity[]: {size} bytes in {count} objects (avg size {objSize} bytes)\n" +
				"\t{class}.doTest: {size} bytes in {count} objects (avg size {objSize} bytes)\n",
			"class=" + getClass().getName(),
			"size=" + TestUtil.fmt(objSize * COUNT),
			"count=" + TestUtil.fmt(COUNT),
			"objSize=" + objSize);
	}

	public void doTest() throws Exception {
		for (int i = 0; i < COUNT; i++)
			Array.newInstance(Entity.class, LENGTH);
	}

	private static class Entity {}
}
