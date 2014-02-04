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

/**
* @author Dmitry Paraschenko
*/
public final class LocationStack {
	int invocation_point_loc = AProfRegistry.UNKNOWN_LOC;
	int invocation_point_count;
	int invoked_method_loc = AProfRegistry.UNKNOWN_LOC;
	int invoked_method_count = 0;

	private static final ThreadLocal<LocationStack> LOCATION_STACK = new ThreadLocal<LocationStack>() {
		@Override
		protected LocationStack initialValue() {
			return new LocationStack();
		}
	};

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

	public LocationStack pushStackAndForceInvokedMethod(int loc) {
		LocationStack savedCopy = new LocationStack();
		savedCopy.copyFrom(this);
		invocation_point_loc = AProfRegistry.UNKNOWN_LOC;
		invocation_point_count = 1;
		invoked_method_loc = loc;
		invoked_method_count = 1;
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
	}
}
