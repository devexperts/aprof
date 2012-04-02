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

/**
 * @author Dmitry Paraschenko
 */
public interface TestCase {
	/** Test case name. */
	public String name();

	/** Tested method. */
	public void doTest();

	/** Verifies AProf configuration and returns <code>null</code> if it's valid or the reason otherwise. */
	public String verifyConfiguration(Configuration configuration);

	/** Returns expected memory usage statistics for single invocation of the {@link #doTest()} method. */
	public String getExpectedStatistics();
}
