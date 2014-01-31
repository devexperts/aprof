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
class TryTest implements TestCase {
	private static final int COUNT = 100000;

	public String name() {
		return "try";
	}

	public String verifyConfiguration(Configuration configuration) {
		return null;
	}

	public String[] getCheckedClasses() {
		return new String[] {Double.class.getName(), Float.class.getName()};
	}

	public String getExpectedStatistics() {
		int doubleObjSize = AProfSizeUtil.getObjectSize(new Double(0)) << AProfSizeUtil.SIZE_SHIFT;
		int floatObjSize = AProfSizeUtil.getObjectSize(new Float(0)) << AProfSizeUtil.SIZE_SHIFT;
		return fmt(
			"Allocated {size2} bytes in {count2} objects in 2 locations of 2 classes\n" +
			"-------------------------------------------------------------------------------\n" +
			"java.lang.Double: {doubleSize} (_%) bytes in {count} (_%) objects (avg size {doubleObjSize} bytes)\n" +
			"\tjava.lang.Double.valueOf: {doubleSize} (_%) bytes in {count} (_%) objects\n" +
			"\t\t{class}.doTest: {doubleSize} (_%) bytes in {count} (_%) objects\n" +
			"\n" +
			"java.lang.Float: {floatSize} (_%) bytes in {count} (_%) objects (avg size {floatObjSize} bytes)\n" +
			"\t{class}.doTest: {floatSize} (_%) bytes in {count} (_%) objects\n",
			"class=" + getClass().getName(),
			"doubleSize=" + fmt(doubleObjSize * COUNT),
			"floatSize=" + fmt(floatObjSize * COUNT),
			"count=" + fmt(COUNT),
			"size2=" + fmt((doubleObjSize + floatObjSize) * COUNT),
			"count2=" + fmt(2 * COUNT),
			"doubleObjSize=" + doubleObjSize,
			"floatObjSize=" + floatObjSize);
	}

	public void doTest() {
		for (int i = 0; i < COUNT; i++) {
			try {
				Double.valueOf("16r7");
			} catch (NumberFormatException e) {
				new Float(10.6);
			}
		}
	}
}
