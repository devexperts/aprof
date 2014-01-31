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

import java.util.Comparator;

import com.devexperts.aprof.util.IndexMap;

/**
* @author Dmitry Paraschenko
*/
final class DatatypeInfo {
	private final String name;
	private final IndexMap index;
	private volatile int size;
	private volatile boolean directClone;

	public DatatypeInfo(String name, IndexMap index) {
		this.name = name;
		this.index = index;
	}

	public String getName() {
		return name;
	}

	public boolean isArray() {
		return index.hasHistogram();
	}

	public IndexMap getIndex() {
		return index;
	}

	public boolean isDirectClone() {
		return directClone;
	}

	public void setDirectClone(boolean directClone) {
		this.directClone = directClone;
	}

	public int getSize() {
		return size;
	}

	public void setSize(int size) {
		this.size = size;
	}

	public static final Comparator<DatatypeInfo> COMPARATOR_NAME = new Comparator<DatatypeInfo>() {
		public int compare(DatatypeInfo o1, DatatypeInfo o2) {
			return o1.name.compareTo(o2.name);
		}
	};
}
