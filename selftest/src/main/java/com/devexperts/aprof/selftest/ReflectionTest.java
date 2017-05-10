package com.devexperts.aprof.selftest;

/*-
 * #%L
 * Aprof Integration tests (selftest)
 * %%
 * Copyright (C) 2002 - 2017 Devexperts, LLC
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */

import java.lang.reflect.Constructor;

import com.devexperts.aprof.AProfSizeUtil;
import com.devexperts.aprof.Configuration;

/**
 * @author Dmitry Paraschenko
 */
class ReflectionTest implements TestCase {
	private static final int COUNT = 1000000;

	public String name() {
		return "reflection";
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
			"{class}$Entity: {size} bytes in {count} objects (avg size {objSize} bytes)\n" +
				"\tsun.reflect.GeneratedConstructorAccessor.newInstance: {size} bytes in {count} objects\n" +
				"\t\tjava.lang.reflect.Constructor.newInstance: {size} bytes in {count} objects\n" +
				"\t\t\t{class}.doTest: {size} bytes in {count} objects\n",
			"class=" + getClass().getName(),
			"size=" + TestUtil.fmt(objSize * COUNT),
			"count=" + TestUtil.fmt(COUNT),
			"objSize=" + objSize);
	}

	public void doTest() throws Exception {
		Constructor<Entity> constructor = Entity.class.getConstructor();
		for (int i = 0; i < COUNT; i++)
			constructor.newInstance();
	}

	private static class Entity {
		public Entity() {
		}
	}

}
