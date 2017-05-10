package com.devexperts.aprof;

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
