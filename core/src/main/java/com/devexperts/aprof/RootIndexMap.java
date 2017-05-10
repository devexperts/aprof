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

public class RootIndexMap extends IndexMap {
	/**
	 * The corresponding datatype.
	 */
	private final DatatypeInfo datatypeInfo;

	/**
	 * Root index in AProfRegistry rootIndexes.
	 */
	private final int rootIndex;

	/**
	 * Allocation was possibly eliminated by HotSpot.
	 */
	private boolean possiblyEliminatedAllocation;

	RootIndexMap(int location, int rootIndex, int[] histogram, DatatypeInfo datatypeInfo) {
		super(location, histogram);
		this.datatypeInfo = datatypeInfo;
		this.rootIndex = rootIndex;
	}

	DatatypeInfo getDatatypeInfo() {
		return datatypeInfo;
	}

	int getRootIndex() {
		return rootIndex;
	}

	public boolean isPossiblyEliminatedAllocation() {
		return possiblyEliminatedAllocation;
	}

	public void setPossiblyEliminatedAllocation() {
		possiblyEliminatedAllocation = true;
	}
}
