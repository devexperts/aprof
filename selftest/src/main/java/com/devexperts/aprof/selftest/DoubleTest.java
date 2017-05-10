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

/**
 * @author Dmitry Paraschenko
 */
class DoubleTest implements TestCase {
	private static final int COUNT = 1000000;
	private static Double temp; // prevent elimination

	public String name() {
		return "double";
	}

	public String verifyConfiguration(Configuration config) {
		return null;
	}

	public String[] getCheckedClasses() {
		return new String[] {Double.class.getName()};
	}

	public String getExpectedStatistics(Configuration config) {
		long objSize = AProfSizeUtil.getObjectSize(new Double(0));
		return TestUtil.fmt(
			"java.lang.Double: {size} bytes in {count} objects (avg size {objSize} bytes)\n" +
				"\tjava.lang.Double.valueOf: {size} bytes in {count} objects\n" +
				"\t\t{class}.doTest: {size} bytes in {count} objects\n",
			"class=" + getClass().getName(),
			"size=" + TestUtil.fmt(objSize * COUNT),
			"count=" + TestUtil.fmt(COUNT),
			"objSize=" + objSize);
	}

	public void doTest() {
		for (int i = 0; i < COUNT; i++) {
			if (i % 2 == 0)
				temp = (double)(10000 + i);
			else
				temp = Double.valueOf(Double.toString(10000 + i));
		}
	}

}
