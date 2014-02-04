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

import static com.devexperts.aprof.AProfRegistry.getRootIndex;
import static com.devexperts.aprof.AProfSizeUtil.*;

/**
 * Methods that are instrumented into "internal" target code by aprof method transformer.
 */
@SuppressWarnings({"UnusedDeclaration"})
public class AProfOpsInternal extends AProfOps {
	public static void allocate(LocationStack stack, int index) {
		getRootIndex(index).increment();
	}

	public static void allocateSize(LocationStack stack, int index, Class objectClass) {
		RootIndexMap rootIndex = getRootIndex(index);
		rootIndex.increment();
		DatatypeInfo datatypeInfo = rootIndex.getDatatypeInfo();
		if (datatypeInfo.getSize() == 0)
			datatypeInfo.setSize(getObjectSizeByClass(objectClass));
	}

	public static void allocateArraySize(boolean[] o, LocationStack stack, int index) {
		IndexMap map = getRootIndex(index);
		int size = getArraySize(o);
		map.increment(o.length, size);
	}

	public static void allocateArraySize(byte[] o, LocationStack stack, int index) {
		IndexMap map = getRootIndex(index);
		int size = getArraySize(o);
		map.increment(o.length, size);
	}

	public static void allocateArraySize(char[] o, LocationStack stack, int index) {
		IndexMap map = getRootIndex(index);
		int size = getArraySize(o);
		map.increment(o.length, size);
	}

	public static void allocateArraySize(short[] o, LocationStack stack, int index) {
		IndexMap map = getRootIndex(index);
		int size = getArraySize(o);
		map.increment(o.length, size);
	}

	public static void allocateArraySize(int[] o, LocationStack stack, int index) {
		IndexMap map = getRootIndex(index);
		int size = getArraySize(o);
		map.increment(o.length, size);
	}

	public static void allocateArraySize(long[] o, LocationStack stack, int index) {
		IndexMap map = getRootIndex(index);
		int size = getArraySize(o);
		map.increment(o.length, size);
	}

	public static void allocateArraySize(float[] o, LocationStack stack, int index) {
		IndexMap map = getRootIndex(index);
		int size = getArraySize(o);
		map.increment(o.length, size);
	}

	public static void allocateArraySize(double[] o, LocationStack stack, int index) {
		IndexMap map = getRootIndex(index);
		int size = getArraySize(o);
		map.increment(o.length, size);
	}

	public static void allocateArraySize(Object[] o, LocationStack stack, int index) {
		IndexMap map = getRootIndex(index);
		int size = getArraySize(o);
		map.increment(o.length, size);
	}

	public static void allocateArraySizeMulti(Object[] o, LocationStack stack, int index) {
		IndexMap map = getRootIndex(index);
		int size = getArraySizeMultiRec(o);
		map.increment(o.length, size);
	}
}
