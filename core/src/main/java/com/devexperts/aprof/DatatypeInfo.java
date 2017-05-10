package com.devexperts.aprof;

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

import java.util.Comparator;

/**
* @author Dmitry Paraschenko
*/
public final class DatatypeInfo {
	/**
	 * Name of this data type.
	 */
	private final String name;

	/**
	 * Index of this data type. Its children are of type {@link RootIndexMap} and
	 * correspond to root locations where this data type is allocated.
	 */
	private final IndexMap<RootIndexMap> index;

	/**
	 * Fixed size of this data type. 0 for array, -1 if failed to determine.
	 */
	private volatile long size;

	/**
	 * True if this data type does not override clone nor any of its super-classes do.
	 */
	private volatile boolean directClone;

	public DatatypeInfo(String name, int[] histogram) {
		this.name = name;
		this.index = new IndexMap<RootIndexMap>(AProfRegistry.UNKNOWN_LOC, histogram);
	}

	public String getName() {
		return name;
	}

	public boolean isArray() {
		return index.hasHistogram();
	}

	public IndexMap<RootIndexMap> getIndex() {
		return index;
	}

	public boolean isDirectClone() {
		return directClone;
	}

	public void setDirectClone(boolean directClone) {
		this.directClone = directClone;
	}

	public long getSize() {
		return size;
	}

	public void setSize(long size) {
		this.size = size;
	}

	public static final Comparator<DatatypeInfo> COMPARATOR_NAME = new Comparator<DatatypeInfo>() {
		public int compare(DatatypeInfo o1, DatatypeInfo o2) {
			return o1.name.compareTo(o2.name);
		}
	};
}
