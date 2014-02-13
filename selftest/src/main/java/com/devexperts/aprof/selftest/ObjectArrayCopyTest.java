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

import java.util.Arrays;

import com.devexperts.aprof.AProfSizeUtil;
import com.devexperts.aprof.Configuration;

class ObjectArrayCopyTest implements TestCase {
	private static final int COUNT = 100000;
	private static Object temp; // prevent elimination

	private static Entity[] ARRAY = new Entity[5];

	public String name() {
		return "objectArrayCopy";
	}

	public String verifyConfiguration(Configuration config) {
		return null;
	}

	public String[] getCheckedClasses() {
		return new String[] { getClass().getName() + "$Entity[]" };
	}

	public String getExpectedStatistics(Configuration config) {
		long objSize = AProfSizeUtil.getObjectSize(ARRAY);
		return TestUtil.fmt(
			"{class}$Entity[]: {size} bytes in {count} objects (avg size {objSize} bytes)\n" +
				"\tjava.util.Arrays.copyOf: {size} bytes in {count} objects (avg size {objSize} bytes)\n" +
				"\t\t{class}.doTest: {size} bytes in {count} objects (avg size {objSize} bytes)\n",
			"class=" + getClass().getName(),
			"size=" + TestUtil.fmt(objSize * COUNT),
			"count=" + TestUtil.fmt(COUNT),
			"objSize=" + objSize);
	}

	public void doTest() throws Exception {
		for (int i = 0; i < COUNT; i++)
			temp = Arrays.copyOf(ARRAY, ARRAY.length);
	}

	private static class Entity {
		int value;

		private Entity(int value) {
			this.value = value;
		}
	}
}
