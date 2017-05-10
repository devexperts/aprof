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

class TrackingIntfTest implements TestCase {
	private static final int COUNT = 100000;
	private static float[] temp; // prevent elimination

	public String name() {
		return "trackingIntf";
	}

	public String verifyConfiguration(Configuration config) {
		return null;
	}

	public String[] getCheckedClasses() {
		return new String[] { "float[]" };
	}

	public String getExpectedStatistics(Configuration config) {
		long objSize = AProfSizeUtil.getObjectSize(new float[1]);
		return TestUtil.fmt(
			"float[]: {size} bytes in {count} objects (avg size {objSize} bytes)\n" +
			"\t{impl}.allocateResult: {size} bytes in {count} objects (avg size {objSize} bytes)\n" +
			"\t\t{impl}.trackedMethod: {size} bytes in {count} objects (avg size {objSize} bytes)\n" +
			"\t\t\t{class}.invokeTrackedViaIntf: {size} bytes in {count} objects (avg size {objSize} bytes)\n",
			"class=" + getClass().getName(),
			"impl=" + TrackingIntfTrackedImpl.class.getName(),
			"size=" + TestUtil.fmt(objSize * COUNT),
			"count=" + TestUtil.fmt(COUNT),
			"objSize=" + objSize);
	}

	public void doTest() throws ClassNotFoundException, IllegalAccessException, InstantiationException {
		TrackingIntf impl = new TrackingIntfTrackedImpl();
		for (int i = 0; i < COUNT; i++)
			invokeTrackedViaIntf(impl);
	}

	private void invokeTrackedViaIntf(TrackingIntf impl) {
		temp = impl.trackedMethod(); // interface call to tracked implementation
	}
}
