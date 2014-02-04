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

class StringTest implements TestCase {
	private static final int COUNT = 1000000;

	private static final char[] CHARS = "TEST".toCharArray();
	private static int temp; // prevents optimizations

	public String name() {
		return "string";
	}

	public String verifyConfiguration(Configuration configuration) {
		return null;
	}

	public String[] getCheckedClasses() {
		return new String[] {String.class.getName(), "char[]"};
	}

	public String getExpectedStatistics() {
		int charsObjSize = AProfSizeUtil.getObjectSize(CHARS) << AProfSizeUtil.SIZE_SHIFT;
		int stringObjSize = AProfSizeUtil.getObjectSize(new String()) << AProfSizeUtil.SIZE_SHIFT;
		return fmt(
			"char[]: {charsSize} bytes in {count} objects (avg size {charsObjSize} bytes)\n" +
			"\tjava.util.Arrays.copyOf: {charsSize} bytes in {count} objects (avg size {charsObjSize} bytes)\n" +
			"\t\tjava.lang.String.<init>: {charsSize} bytes in {count} objects (avg size {charsObjSize} bytes)\n" +
			"\t\t\t{class}.doTest: {charsSize} bytes in {count} objects (avg size {charsObjSize} bytes)\n" +
			"\n" +
			"java.lang.String: {stringSize} bytes in {count} objects (avg size {stringObjSize} bytes)\n" +
			"\t{class}.doTest: {stringSize} bytes in {count} objects\n",
			"class=" + getClass().getName(),
			"charsSize=" + fmt(charsObjSize * COUNT),
			"stringSize=" + fmt(stringObjSize * COUNT),
			"count=" + fmt(COUNT),
			"charsObjSize=" + charsObjSize,
			"stringObjSize=" + stringObjSize);
	}

	public void doTest() {
		for (int i = 0; i < COUNT; i++)
			temp += new String(CHARS).length();
	}
}
