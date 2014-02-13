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

import java.util.*;

import com.devexperts.aprof.AProfSizeUtil;
import com.devexperts.aprof.Configuration;

class ArraySizeTest implements TestCase {
	private static final int COUNT = 10000;
	private static final int MAX_LENGTH = 20;

	public String name() {
		return "arraySize";
	}

	public String verifyConfiguration(Configuration config) {
		return null;
	}

	public String[] getCheckedClasses() {
		return new String[] {
			"boolean[]",
			"byte[]",
			"short[]",
			"char[]",
			"int[]",
			"long[]",
			"float[]",
			"double[]",
			"java.lang.Object[]",
		};
	}

	public String getExpectedStatistics(Configuration config) {
		LinkedHashMap<String, Long> totalSize = new LinkedHashMap<String, Long>();
		for (int j = 0; j < MAX_LENGTH; j++) {
			inc(totalSize, "boolean", AProfSizeUtil.getObjectSize(new boolean[j]));
			inc(totalSize, "byte", AProfSizeUtil.getObjectSize(new byte[j]));
			inc(totalSize, "char", AProfSizeUtil.getObjectSize(new char[j]));
			inc(totalSize, "double", AProfSizeUtil.getObjectSize(new double[j]));
			inc(totalSize, "float", AProfSizeUtil.getObjectSize(new float[j]));
			inc(totalSize, "int", AProfSizeUtil.getObjectSize(new int[j]));
			inc(totalSize, "java.lang.Object", AProfSizeUtil.getObjectSize(new Object[j]));
			inc(totalSize, "long", AProfSizeUtil.getObjectSize(new long[j]));
			inc(totalSize, "short", AProfSizeUtil.getObjectSize(new short[j]));
		}
		ArrayList<String> kvs = new ArrayList<String>();
		long sizeSum = 0;
		for (String name : totalSize.keySet()) {
			long size = totalSize.get(name);
			kvs.add(name + "Size=" + TestUtil.fmt(size * COUNT));
			kvs.add(name + "AvgSize=" + TestUtil.fmt((int)Math.round((double)size * COUNT / (COUNT * MAX_LENGTH))));
			sizeSum += size;
		}
		kvs.add("class=" + getClass().getName());
		kvs.add("count=" + TestUtil.fmt(COUNT * MAX_LENGTH));
		kvs.add("size=" + TestUtil.fmt(COUNT * sizeSum));
		StringBuilder expected = new StringBuilder();
		for (String name : totalSize.keySet()) {
			expected.append(
				name + "[]: {" + name + "Size} bytes in {count} objects (avg size {" + name + "AvgSize} bytes)\n" +
				"\tcom.devexperts.aprof.selftest.ArraySizeTest.doTest: {" + name + "Size} bytes in {count} objects (avg size {" + name + "AvgSize} bytes)\n" +
				"\n");
		}
		return TestUtil.fmt(expected.toString(), kvs.toArray(new String[kvs.size()]));
	}

	private void inc(Map<String, Long> totalSize, String name, long size) {
		Long prev = totalSize.get(name);
		totalSize.put(name, (prev == null ? 0 : prev) + size);
	}

	public void doTest() throws Exception {
		for (int i = 0; i < COUNT; i++)
			for (int j = 0; j < MAX_LENGTH; j++) {
				use(new boolean[j]);
				use(new byte[j]);
				use(new short[j]);
				use(new char[j]);
				use(new int[j]);
				use(new long[j]);
				use(new float[j]);
				use(new double[j]);
				use(new Object[j]);
			}
	}

	private void use(Object o) {
		// just do nothing here
	}
}
