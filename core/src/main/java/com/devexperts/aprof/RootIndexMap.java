package com.devexperts.aprof;

class RootIndexMap extends IndexMap {
	private final DatatypeInfo datatypeInfo;

	RootIndexMap(int location, int index, int[] histogram, DatatypeInfo datatypeInfo) {
		super(location, index, histogram);
		this.datatypeInfo = datatypeInfo;
	}

	DatatypeInfo getDatatypeInfo() {
		return datatypeInfo;
	}
}
