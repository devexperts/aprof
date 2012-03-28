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

import java.util.Arrays;

/**
 * @author Dmitry Paraschenko
 */
public class StringIndexer {
	private static final int MAGIC = 0xB46394CD;
	private static final int MAX_SHIFT = 29;
	private static final int THRESHOLD = (int)((1L << 32) * 0.5); // 50% fill factor for speed

	private static class Core {
		final int shift;
		final int length;
		final String[] strings;
		final int[] ids;
        final int[] id2pos;

		Core(int shift) {
			this.shift = shift;
			length = 1 << (32 - shift);
			strings = new String[length];
			ids = new int[length];
            id2pos = new int[length];
            Arrays.fill(id2pos, -1);
		}
	}

	private volatile Core core = new Core(MAX_SHIFT);
	private volatile int size;

	// does not need external synchronization
	public int register(String string) {
        return get(string, true);
	}

	// does not need external synchronization
	public int get(String string) {
        return get(string, false);
	}

	// does not need external synchronization
	private int get(String string, boolean register) {
		Core core = this.core; // atomic read;
		int i = (string.hashCode() * MAGIC) >>> core.shift;
		String s;
		while (!string.equals(s = core.strings[i])) {
			if (s == null) {
                if (!register) {
                    return 0;
                }
                synchronized (this) {
                    int id = ++size;
                    core = this.core;
                    core.strings[i] = string;
                    core.ids[i] = id;
                    core.id2pos[id] = i;
                    if (size >= (THRESHOLD >>> core.shift))
				        rehash();
                    return id;
                }
            }

			if (i == 0)
				i = core.length;
			i--;
		}
		return core.ids[i];
	}

	// does not need external synchronization
	public String get(int id) {
		Core core = this.core; // atomic read;
        if (id >= core.length) {
            return null;
        }
        int pos = core.id2pos[id];
        if (pos < 0) {
            return null;
        }
        return core.strings[pos];
	}

    public int size() {
        return size;
    }

    private boolean putInternal(Core core, String string, int id) {
		int i = (string.hashCode() * MAGIC) >>> core.shift;
		String k;
		while (!string.equals(k = core.strings[i])) {
			if (k == null) {
				core.strings[i] = string;
				core.ids[i] = id;
                core.id2pos[id] = i;
				return true;
			}
			if (i == 0)
				i = core.length;
			i--;
		}
		core.ids[i] = id;
		return false;
	}

	private void rehash() {
		Core old_core = core;
		Core new_core = new Core(old_core.shift - 1);
		for (int i = 0; i < old_core.length; i++)
			if (old_core.strings[i] != null)
				putInternal(new_core, old_core.strings[i], old_core.ids[i]);
		core = new_core;
	}
}
