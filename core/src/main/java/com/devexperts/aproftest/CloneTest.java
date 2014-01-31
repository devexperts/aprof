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
class CloneTest implements TestCase {
	private static final int COUNT = 1000000;

	public String name() {
		return "clone";
	}

	public String verifyConfiguration(Configuration configuration) {
		return null;
	}

	public String[] getCheckedClasses() {
		return new String[] {getClass().getName() + "$"};
	}

	public String getExpectedStatistics() {
		int objSize = AProfSizeUtil.getObjectSize(new Entity()) << AProfSizeUtil.SIZE_SHIFT;
		return fmt(
			"Allocated {size1} bytes in {count1} objects in 2 locations of 1 classes\n" +
			"-------------------------------------------------------------------------------\n" +
			"{class}$Entity: {size1} (_%) bytes in {count1} (_%) objects (avg size {objSize} bytes)\n" +
			"\t{class}$Entity.dup*: {size} (_%) bytes in {count} (_%) objects\n" +
			"\t{class}.doTest: {objSize} (_%) bytes in 1 (_%) objects\n",
			"class=" + getClass().getName(),
			"size=" + fmt(objSize * COUNT),
			"count=" + fmt(COUNT),
			"size1=" + fmt(objSize * (COUNT + 1)),
			"count1=" + fmt(COUNT + 1),
			"objSize=" + objSize);
	}

	public void doTest() {
		Entity entity = new Entity();
		for (int i = 0; i < COUNT; i++)
			entity.dup();
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
}
