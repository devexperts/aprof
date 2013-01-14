/*
 * Aprof - Java Memory Allocation Profiler
 * Copyright (C) 2002-2013  Devexperts LLC
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

import com.devexperts.aprof.Configuration;

/**
 * @author Dmitry Paraschenko
 */
class CloneTest implements TestCase {
	public String name() {
		return "clone";
	}

	public String verifyConfiguration(Configuration configuration) {
		return null;
	}

	public String[] getCheckedClasses() {
		return new String[] {getClass().getCanonicalName() + "$"};
	}

	public String getExpectedStatistics() {
		return STATISTICS;
	}

	public void doTest() {
		long time = System.currentTimeMillis();
		Entity entity = new Entity();
		for (int i = 0; i < 10000000; i++) {
			if (i % 1000000 == 0)
				System.out.print('.');
			entity.dup();
		}
		System.out.printf(" Test took %d ms\n", System.currentTimeMillis() - time);
	}

	private static class Entity implements Cloneable {
		public Entity dup() {
			try {
				return (Entity) clone();
			} catch (CloneNotSupportedException e) {
				throw new InternalError();
			}
		}
	}

	private static String STATISTICS = "" +
			"Allocated 8 bytes in 1 objects in 2 locations of 1 classes\n" +
			"-------------------------------------------------------------------------------\n" +
			"com.devexperts.aproftest.CloneTest$Entity: 8 (100%) bytes in 1 (100%) objects (avg size 8 bytes)\n" +
			"\tcom.devexperts.aproftest.CloneTest$Entity.dup*: 80,000,000 (1,000,000,000%) bytes in 10,000,000 (1,000,000,000%) objects\n" +
			"\tcom.devexperts.aproftest.CloneTest.doTest: 8 (100%) bytes in 1 (100%) objects\n" +
			"";
}
