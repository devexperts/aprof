package com.devexperts.aprof.util;

/*-
 * #%L
 * Aprof Core
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

import com.devexperts.aprof.Internal;

/**
 * @author Roman Elizarov
 */
@Internal
public class FastArrayList<T> {
	private volatile T[] list;

	@SuppressWarnings("unchecked")
	public FastArrayList() {
		list = (T[]) new Object[1024];
	}

	public T getSafely(int i) {
		// try to read w/o synchronization first
		T result = getUnsync(i);
		// double-checked locking
		if (result == null)
			synchronized (this) {
				return list[i];
			}
		return result;
	}

	// requires external synchronization
	public T getUnsync(int i) {
		T[] curList = list;
		return i < curList.length ? curList[i] : null;
	}

	// requires external synchronization
	@SuppressWarnings("unchecked")
	public void putUnsync(int i, T val) {
		int n = list.length;
		if (i >= n) {
			T[] a = (T[]) new Object[Math.max(2 * n, i + 1)];
			System.arraycopy(list, 0, a, 0, n);
			list = a;
		}
		list[i] = val;
	}

	public int length() {
		return list.length;
	}
}
