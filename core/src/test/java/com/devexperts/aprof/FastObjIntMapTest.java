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

package com.devexperts.aprof;

import com.devexperts.aprof.util.FastObjIntMap;
import junit.framework.TestCase;

/**
 * @author Roman Elizarov
 */
public class FastObjIntMapTest extends TestCase {
	public void testBasic() {
		FastObjIntMap<String> m = new FastObjIntMap<String>();
		int cnt = 1000;
		for (int i = 0; i < cnt; i++)
			m.put(String.valueOf(i).intern(), i);
		for (int i = 0; i < cnt; i++)
			assertEquals(i, m.get(String.valueOf(i).intern()));
	}
}
