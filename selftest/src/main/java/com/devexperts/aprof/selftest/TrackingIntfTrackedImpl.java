package com.devexperts.aprof.selftest;

/**
 * @author Roman Elizarov
 */
public class TrackingIntfTrackedImpl implements TrackingIntf {
	public float[] trackedMethod() {
		return allocateResult();
	}

	private float[] allocateResult() {
		return new float[] { 1 };
	}
}
