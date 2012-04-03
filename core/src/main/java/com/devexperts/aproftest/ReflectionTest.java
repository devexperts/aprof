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

import java.lang.reflect.Constructor;

/**
 * @author Dmitry Paraschenko
 */
class ReflectionTest implements TestCase {
	public String name() {
		return "reflection";
	}

	public String verifyConfiguration(Configuration configuration) {
		return null;
	}

	public String[] getCheckedClasses() {
		return null;
	}

	public String getExpectedStatistics() {
		return STATISTICS;
	}

	public void doTest() {
		long time = System.currentTimeMillis();
		try {
			Constructor<Entity> constructor = Entity.class.getConstructor();
			for (int i = 0; i < 10000000; i++) {
				if (i % 1000000 == 0)
					System.out.print('.');
				constructor.newInstance();
			}
			System.out.printf(" Test took %d ms\n", System.currentTimeMillis() - time);
		} catch (Exception e) {
			System.out.printf(" Test failed in %d ms\n", System.currentTimeMillis() - time);
			e.printStackTrace();
		}
	}

	private static class Entity {
		public Entity() {
		}
	}

	private static String STATISTICS = "" +
			"Allocated 80,000,000 bytes in 10,000,000 objects in 1 locations of 1 classes\n" +
			"-------------------------------------------------------------------------------\n" +
			"com.devexperts.aproftest.ReflectionTest$Entity: 80,000,000 (100%) bytes in 10,000,000 (100%) objects (avg size 8 bytes)\n" +
			"\tsun.reflect.GeneratedConstructorAccessor.newInstance: 80,000,000 (100%) bytes in 10,000,000 (100%) objects\n" +
			"\t\tjava.lang.reflect.Constructor.newInstance: 80,000,000 (100%) bytes in 10,000,000 (100%) objects\n" +
			"\t\t\tcom.devexperts.aproftest.ReflectionTest.doTest: 80,000,000 (100%) bytes in 10,000,000 (100%) objects\n" +
			"";
}
