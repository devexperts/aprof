package com.devexperts.aprof;

/**
* @author Roman Elizarov
*/
@Internal
class LocationStackThreadLocal extends ThreadLocal<LocationStack> {
	@Override
	protected LocationStack initialValue() {
		return new LocationStack();
	}
}
