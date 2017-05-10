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

import java.util.*;

import com.devexperts.aprof.dump.SnapshotDeep;
import junit.framework.TestCase;

/**
 * @author Roman Elizarov
 */
public class QSortTest extends TestCase {
	public void testQSort() {
		Random r = new Random(1);
		SnapshotDeep cs = new SnapshotDeep("test", false, 0);
		int cnt = 1000;
		cs.ensureChildrenCapacity(cnt);
		for (int i = 0; i < cnt; i++)
			cs.getOrCreateChildAt(0, String.valueOf(i));
		for (int k = 0; k < 100; k++) {
			Collections.shuffle(Arrays.asList(cs.getChildren()), r);
			cs.sortChildrenDeep(SnapshotDeep.COMPARATOR_NAME);
			SnapshotDeep[] lsa = cs.getChildren();
			for (int i = 1; i < cnt; i++)
				if (lsa[i - 1].getName().compareTo(lsa[i].getName()) >= 0)
					fail("Failed to sort at #" + i);
		}
	}
}
