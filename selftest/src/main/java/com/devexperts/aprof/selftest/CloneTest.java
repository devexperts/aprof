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

/**
 * @author Dmitry Paraschenko
 */
class CloneTest implements TestCase {
	private static final int COUNT = 100000;

	public String name() {
		return "clone";
	}

	public String verifyConfiguration(Configuration config) {
		return null;
	}

	public String[] getCheckedClasses() {
		return new String[] {getClass().getName() + "$"};
	}

	public String getExpectedStatistics(Configuration config) {
		long objSize = AProfSizeUtil.getObjectSize(new Entity());
		return TestUtil.fmt(
			"{class}$Entity: {size1} bytes in {count1} objects (avg size {objSize} bytes)\n" +
				"\t{class}$Entity.dup;via-clone: {size} bytes in {count} objects\n" +
				"\t{class}.doTest: {objSize} bytes in 1 objects\n",
			"class=" + getClass().getName(),
			"size=" + TestUtil.fmt(objSize * COUNT),
			"count=" + TestUtil.fmt(COUNT),
			"size1=" + TestUtil.fmt(objSize * (COUNT + 1)),
			"count1=" + TestUtil.fmt(COUNT + 1),
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
