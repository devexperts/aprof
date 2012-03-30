/*
 *  Aprof - Java Memory Allocation Profiler
 *  Copyright (C) 2002-2012  Devexperts LLC
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.devexperts.aprof;

import com.devexperts.aprof.util.IndexMap;


import static com.devexperts.aprof.AProfRegistry.*;
import static com.devexperts.aprof.ArraySizeHelper.*;

/**
 * @author Dmitry Paraschenko
 */
@SuppressWarnings({"UnusedDeclaration"})
public class AProfOps {
	public static void allocate(int index) {
		IndexMap map = getDetailedIndex(index);
		map.increment();
	}

	public static void allocateArraySize(boolean[] o, int index) {
		IndexMap map = getDetailedIndex(index);
		int size = getArraySize(o);
		map.increment(o.length, size);
	}

	public static void allocateArraySize(byte[] o, int index) {
		IndexMap map = getDetailedIndex(index);
		int size = getArraySize(o);
		map.increment(o.length, size);
	}

	public static void allocateArraySize(char[] o, int index) {
		IndexMap map = getDetailedIndex(index);
		int size = getArraySize(o);
		map.increment(o.length, size);
	}

	public static void allocateArraySize(short[] o, int index) {
		IndexMap map = getDetailedIndex(index);
		int size = getArraySize(o);
		map.increment(o.length, size);
	}

	public static void allocateArraySize(int[] o, int index) {
		IndexMap map = getDetailedIndex(index);
		int size = getArraySize(o);
		map.increment(o.length, size);
	}

	public static void allocateArraySize(long[] o, int index) {
		IndexMap map = getDetailedIndex(index);
		int size = getArraySize(o);
		map.increment(o.length, size);
	}

	public static void allocateArraySize(float[] o, int index) {
		IndexMap map = getDetailedIndex(index);
		int size = getArraySize(o);
		map.increment(o.length, size);
	}

	public static void allocateArraySize(double[] o, int index) {
		IndexMap map = getDetailedIndex(index);
		int size = getArraySize(o);
		map.increment(o.length, size);
	}

	public static void allocateArraySize(Object[] o, int index) {
		IndexMap map = getDetailedIndex(index);
		int size = getArraySize(o);
		map.increment(o.length, size);
	}

	public static void allocateArraySizeMulti(Object[] o, int index) {
		IndexMap map = getDetailedIndex(index);
		int size = getArraySizeMultiRec(o);
		map.increment(o.length, size);
	}

	public static void allocateReflect(Object o, int loc) {
		String name = o.getClass().getName();
		IndexMap map = getDetailedIndex(name, loc);
		map.increment();
	}

	public static void allocateReflectSize(Object o, int loc) {
		String name = o.getClass().getName();
		IndexMap map = getDetailedIndex(name, loc);
		if (name.startsWith("[")) {
			map.increment(getArraySizeMultiRec(o));
		} else {
			map.increment();
		}
	}

	public static void allocateReflectVClone(Object o, int reflect_index) {
		if (isDirectCloneClass(o.getClass().getName())) {
			allocateReflect(o, reflect_index);
		}
	}

	public static void allocateReflectVCloneSize(Object o, int reflect_index) {
		if (isDirectCloneClass(o.getClass().getName())) {
			allocateReflectSize(o, reflect_index);
		}
	}

	public static void objectInit(Object o) {
		String name = o.getClass().getName();
		DatatypeInfo datatype_info = getDatatypeInfo(name, false);
		if (datatype_info == null) {
			return;
		}
		datatype_info.getIndex().increment();
	}

	public static void objectInitSize(Object o) {
		String name = o.getClass().getName();
		DatatypeInfo datatype_info = getDatatypeInfo(name, false);
		if (datatype_info == null) {
			return;
		}
		if (datatype_info.getSize() == 0) {
			datatype_info.setSize(getObjectSize(o));
		}
		datatype_info.getIndex().increment();
	}

	//================== LOCATION STACK ===================
	public static void markInvocationPoint(int loc) {
		LocationStack.get().addInvocationPoint(loc);
	}

	public static void unmarkInvocationPoint() {
		LocationStack.get().removeInvocationPoint();
	}

	public static void markInvokedMethod(int loc) {
		LocationStack.get().addInvokedMethod(loc);
	}

	public static void unmarkInvokedMethod() {
		LocationStack.get().removeInvokedMethod();
	}

	public static void markInternalInvokedMethod(int loc) {
		LocationStack.get().addInternalInvokedMethod(loc);
	}

	public static void unmarkInternalInvokedMethod() {
		LocationStack.get().removeInternalInvokedMethod();
	}
}
