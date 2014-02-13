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

import com.devexperts.aprof.AProfSizeUtil;
import com.devexperts.aprof.Configuration;

class StringTest implements TestCase {
	private static final int COUNT = 1000000;

	private static final char[] CHARS = "TEST".toCharArray();
	private static String temp; // prevent elimination

	public String name() {
		return "string";
	}

	public String verifyConfiguration(Configuration config) {
		return null;
	}

	public String[] getCheckedClasses() {
		return new String[] {String.class.getName(), "char[]"};
	}

	public String getExpectedStatistics(Configuration config) {
		long charsObjSize = AProfSizeUtil.getObjectSize(CHARS);
		long stringObjSize = AProfSizeUtil.getObjectSize(new String());
		return TestUtil.fmt(
			"char[]: {charsSize} bytes in {count} objects (avg size {charsObjSize} bytes)\n" +
				"\tjava.util.Arrays.copyOf: {charsSize} bytes in {count} objects (avg size {charsObjSize} bytes)\n" +
				"\t\tjava.lang.String.<init>(char[]): {charsSize} bytes in {count} objects (avg size {charsObjSize} bytes)\n" +
				"\t\t\t{class}.doTest: {charsSize} bytes in {count} objects (avg size {charsObjSize} bytes)\n" +
				"\n" +
				"java.lang.String: {stringSize} bytes in {count} objects (avg size {stringObjSize} bytes)\n" +
				"\t{class}.doTest: {stringSize} bytes in {count} objects\n",
			"class=" + getClass().getName(),
			"charsSize=" + TestUtil.fmt(charsObjSize * COUNT),
			"stringSize=" + TestUtil.fmt(stringObjSize * COUNT),
			"count=" + TestUtil.fmt(COUNT),
			"charsObjSize=" + charsObjSize,
			"stringObjSize=" + stringObjSize);
	}

	public void doTest() {
		for (int i = 0; i < COUNT; i++)
			temp = new String(CHARS);
	}
}
