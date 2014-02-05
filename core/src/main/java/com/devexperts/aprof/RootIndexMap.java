package com.devexperts.aprof;

class RootIndexMap extends IndexMap {
	/**
	 * The corresponding datatype.
	 */
	private final DatatypeInfo datatypeInfo;

	/**
	 * Root index in AProfRegistry rootIndexes.
	 */
	private final int rootIndex;

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
}
