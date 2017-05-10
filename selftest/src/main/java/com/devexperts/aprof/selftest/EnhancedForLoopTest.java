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

import com.devexperts.aprof.AProfSizeUtil;
import com.devexperts.aprof.Configuration;
import com.devexperts.aprof.util.UnsafeHolder;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Roman Elizarov
 */
class EnhancedForLoopTest implements TestCase {
	private static final int N = 2;
	private static final int COUNT = 1000000;
	private static int temp; // prevent elimination

	private static final Class<?> ITR_CLASS;
	private static final Object ITR_CLASS_INSTANCE;

	static {
		try {
			Class<?> cls;
			try {
				cls = Class.forName("java.util.ArrayList$Itr");
			} catch (ClassNotFoundException e) {
				cls = Class.forName("java.util.AbstractList$Itr");
			}
			ITR_CLASS = cls;
			ITR_CLASS_INSTANCE = UnsafeHolder.UNSAFE.allocateInstance(cls);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private final List<Integer> list = new ArrayList<Integer>();

	EnhancedForLoopTest() {
		for (int x = 0; x < N; x++)
			list.add(x);
	}

	public String name() {
		return "enhancedForLoop";
	}

	public String verifyConfiguration(Configuration config) {
		return null;
	}

	public String[] getCheckedClasses() {
		return new String[] { ITR_CLASS.getName() };
	}

	public String getExpectedStatistics(Configuration config) {
		long objSize = AProfSizeUtil.getObjectSize(ITR_CLASS_INSTANCE);
		return TestUtil.fmt(
			ITR_CLASS.getName() + ": {size} bytes in {count} objects (avg size {objSize} bytes)\n" +
				"\tjava.util.ArrayList.iterator: {size} bytes in {count} objects\n" +
				"\t\t{class}.doTest: {size} bytes in {count} objects\n",
			"class=" + getClass().getName(),
			"size=" + TestUtil.fmt(objSize * COUNT),
			"count=" + TestUtil.fmt(COUNT),
			"objSize=" + objSize);
	}

	public void doTest() {
		for (int i = 0; i < COUNT; i++)
			for (Integer x : list)
				temp += x;
	}
}
