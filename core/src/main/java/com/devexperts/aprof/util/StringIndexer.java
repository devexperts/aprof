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

/**
 * @author Dmitry Paraschenko
 */
public class StringIndexer {
	private static final int MIN_LENGTH = 8;

	private volatile Core core = new Core(MIN_LENGTH);
	private volatile int size;

	public int size() {
		return size;
	}

	// does not need external synchronization
	public String get(int id) {
		return core.get(id);
	}

	// does not need external synchronization
	public int get(String string) {
		return core.get(string);
	}

	// does not need external synchronization
	public int register(String string) {
		int id = core.get(string);
		if (id == 0) {
			return registerImpl(string);
		}
		return id;
	}

	private synchronized int registerImpl(String string) {
		int id = core.get(string);
		if (id == 0) {
			id = ++size;
			core.register(string, id);
			if (size >= core.length / 2) // 50% fill factor for speed
				rehash();
		}
		return id;
	}

	private void rehash() {
		Core old_core = core;
		Core new_core = new Core(2 * old_core.length);
		for (int i = 0; i < old_core.length; i++)
			if (old_core.strings[i] != null)
				new_core.register(old_core.strings[i], old_core.ids[i]);
		core = new_core;
	}

	private static class Core {
		final int mask;
		final int length;
		final String[] strings;
		final int[] ids;
		final String[] id2string;

		Core(int length) {
			this.length = length;
			mask = length - 1;
			strings = new String[length];
			ids = new int[length];
			id2string = new String[length];
		}

		// does not need external synchronization
		public String get(int id) {
			if (id >= length) {
				return null;
			}
			return id2string[id];
		}

		// does not need external synchronization
		private int get(String string) {
			int i = string.hashCode() & mask;
			String s;
			while (!string.equals(s = strings[i])) {
				if (s == null) {
					return 0;
				}

				if (i == 0)
					i = length;
				i--;
			}
			return ids[i];
		}

		// needs external synchronization
		private void register(String string, int id) {
			int i = string.hashCode() & mask;
			String s;
			while (!string.equals(s = strings[i])) {
				if (s == null) {
					ids[i] = id;
					strings[i] = string;
					id2string[id] = string;
					return;
				}

				if (i == 0)
					i = length;
				i--;
			}
			throw new IllegalStateException();
		}
	}
}
