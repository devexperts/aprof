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

import java.util.Arrays;

import com.devexperts.aprof.AProfSizeUtil;
import com.devexperts.aprof.Configuration;

import static com.devexperts.aproftest.TestUtil.fmt;

class ObjectArrayCopyTest implements TestCase {
	private static final int COUNT = 1000000;

	private static Entity[] ARRAY = new Entity[5];

	public String name() {
		return "objectArrayCopy";
	}

	public String verifyConfiguration(Configuration configuration) {
		return null;
	}

	public String[] getCheckedClasses() {
		return new String[] { getClass().getName() + "$Entity[]" };
	}

	public String getExpectedStatistics() {
		int objSize = AProfSizeUtil.getObjectSize(ARRAY) << AProfSizeUtil.SIZE_SHIFT;
		return fmt(
			"{class}$Entity[]: {size} bytes in {count} objects (avg size {objSize} bytes)\n" +
			"\tjava.util.Arrays.copyOf: {size} bytes in {count} objects (avg size {objSize} bytes)\n" +
			"\t\t{class}.doTest: {size} bytes in {count} objects (avg size {objSize} bytes)\n",
			"class=" + getClass().getName(),
			"size=" + fmt(objSize * COUNT),
			"count=" + fmt(COUNT),
			"objSize=" + objSize);
	}

	public void doTest() throws Exception {
		for (int i = 0; i < COUNT; i++)
			Arrays.copyOf(ARRAY, ARRAY.length);
	}

	private static class Entity {
		int value;

		private Entity(int value) {
			this.value = value;
		}
	}
}
