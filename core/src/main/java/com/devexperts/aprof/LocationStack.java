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

/**
* @author Dmitry Paraschenko
*/
@Internal
public final class LocationStack {
	int invocation_point_loc = AProfRegistry.UNKNOWN_LOC;
	int invocation_point_count;
	int invoked_method_loc = AProfRegistry.UNKNOWN_LOC;
	int invoked_method_count = 0;
	int transform_loc = AProfRegistry.UNKNOWN_LOC; // only used when AProfRegistry.TRACK_TRANSFORM_DETAILS is true

	private static final ThreadLocal<LocationStack> LOCATION_STACK = new LocationStackThreadLocal();

	public static LocationStack get() {
		return LOCATION_STACK.get();
	}

	public void addInvocationPoint(int loc) {
		if (invocation_point_count > 0) {
			invocation_point_count++;
		} else {
			invocation_point_count = 1;
			invocation_point_loc = loc;
		}
	}

	public void removeInvocationPoint() {
		if (invocation_point_count > 1) {
			invocation_point_count--;
		} else {
			invocation_point_count = 0;
			invocation_point_loc = AProfRegistry.UNKNOWN_LOC;
			invoked_method_count = 0;
			invoked_method_loc = AProfRegistry.UNKNOWN_LOC;
		}
	}

	public void addInvokedMethod(int loc) {
		invocation_point_count++;
		if (invoked_method_count > 0) {
			invoked_method_count++;
		} else {
			invoked_method_count = 1;
			invoked_method_loc = loc;
		}
	}

	public void removeInvokedMethod() {
		invocation_point_count--;
		if (invoked_method_count > 1) {
			invoked_method_count--;
		} else {
			invoked_method_count = 0;
			invoked_method_loc = AProfRegistry.UNKNOWN_LOC;
		}
	}

	public LocationStack pushStackForTransform(int loc) {
		LocationStack savedCopy = new LocationStack();
		savedCopy.copyFrom(this);
		if (AProfRegistry.TRACK_TRANSFORM_DETAILS) {
			// clear stack of detailed invocations and remember transformation location separately,
			// so that tracked method details performed during transformation will be available.
			invocation_point_loc = AProfRegistry.UNKNOWN_LOC;
			invocation_point_count = 0;
			invoked_method_loc = AProfRegistry.UNKNOWN_LOC;
			invoked_method_count = 0;
			transform_loc = loc;
		} else {
			// force transform location as an outermost invoked method, so further details on
			// tracked methods inside transform will not be available.
			invocation_point_loc = AProfRegistry.UNKNOWN_LOC;
			invocation_point_count = 1;
			invoked_method_loc = loc;
			invoked_method_count = 1;
		}
		return savedCopy;
	}

	public void popStack(LocationStack savedCopy) {
		copyFrom(savedCopy);
	}

	private void copyFrom(LocationStack other) {
		invocation_point_loc = other.invoked_method_loc;
		invocation_point_count = other.invocation_point_count;
		invoked_method_loc = other.invoked_method_loc;
		invoked_method_count = other.invoked_method_count;
		transform_loc = other.transform_loc;
	}

}
