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

package com.devexperts.aprof.util;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * @author Dmitry Paraschenko
 */
public class FastIntObjMap<V> {
	private static final int MAGIC = 0xB46394CD;
	private static final int MAX_SHIFT = 29;
	private static final int THRESHOLD = (int)((1L << 32) * 0.5); // 50% fill factor for speed

	private static class Core {
		final int shift;
		final int length;
		final int[] keys;
		final Object[] values;

		Core(int shift) {
			this.shift = shift;
			length = 1 << (32 - shift);
			keys = new int[length];
			values = new Object[length];
		}
	}

	private volatile Core core = new Core(MAX_SHIFT);
	private volatile int size;

	public int size() {
		return size;
	}

	// does not need external synchronization
	public V get(int key) {
		Core core = this.core; // atomic read;
		int i = (key * MAGIC) >>> core.shift;
		int k;
		while (key != (k = core.keys[i])) {
			if (k == 0)
				return null;
			if (i == 0)
				i = core.length;
			i--;
		}
		return (V) core.values[i];
	}

	// needs external synchronization
	public void put(int key, V value) {
		if (putInternal(this.core, key, value))
			if (++size >= (THRESHOLD >>> core.shift))
				rehash();
	}

	private boolean putInternal(Core core, int key, Object value) {
		int i = (key * MAGIC) >>> core.shift;
		int k;
		while (key != (k = core.keys[i])) {
			if (k == 0) {
				core.keys[i] = key;
				core.values[i] = value;
				return true;
			}
			if (i == 0)
				i = core.length;
			i--;
		}
		core.values[i] = value;
		return false;
	}

	private void rehash() {
		Core old_core = core;
		Core new_core = new Core(old_core.shift - 1);
		for (int i = 0; i < old_core.length; i++)
			if (old_core.keys[i] != 0)
				putInternal(new_core, old_core.keys[i], old_core.values[i]);
		core = new_core;
	}

	public Iterator<Integer> iterator() {
		return new Iterator<Integer>() {
			int i = 0;
			Core saved_core = core;

			public boolean hasNext() {
				while (i < saved_core.length && (saved_core.keys[i] == 0 || saved_core.values[i] == null)) {
					i++;
				}
				return i < saved_core.length;
			}

			public Integer next() {
				if (!hasNext()) {
					throw new NoSuchElementException();
				}
				return saved_core.keys[i++];
			}

			public void remove() {
				throw new UnsupportedOperationException();
			}
		};
	}
}
