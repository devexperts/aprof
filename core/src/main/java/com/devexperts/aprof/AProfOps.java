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

import com.devexperts.aprof.util.IndexMap;

import static com.devexperts.aprof.AProfRegistry.*;
import static com.devexperts.aprof.AProfSizeUtil.*;

/**
 * @author Dmitry Paraschenko
 */
@SuppressWarnings({"UnusedDeclaration"})
public class AProfOps {
	private static IndexMap getIndex(int index) {
		return getDetailedIndex(LocationStack.get(), index);
	}

	public static void allocate(int index) {
		IndexMap map = getIndex(index);
		map.increment();
	}

	public static void allocate(LocationStack stack, int index) {
		IndexMap map = getDetailedIndex(stack, index);
		map.increment();
	}

	public static void allocateArraySize(boolean[] o, LocationStack stack, int index) {
		IndexMap map = getDetailedIndex(stack, index);
		int size = getArraySize(o);
		map.increment(o.length, size);
	}

	public static void allocateArraySize(byte[] o, LocationStack stack, int index) {
		IndexMap map = getDetailedIndex(stack, index);
		int size = getArraySize(o);
		map.increment(o.length, size);
	}

	public static void allocateArraySize(char[] o, LocationStack stack, int index) {
		IndexMap map = getDetailedIndex(stack, index);
		int size = getArraySize(o);
		map.increment(o.length, size);
	}

	public static void allocateArraySize(short[] o, LocationStack stack, int index) {
		IndexMap map = getDetailedIndex(stack, index);
		int size = getArraySize(o);
		map.increment(o.length, size);
	}

	public static void allocateArraySize(int[] o, LocationStack stack, int index) {
		IndexMap map = getDetailedIndex(stack, index);
		int size = getArraySize(o);
		map.increment(o.length, size);
	}

	public static void allocateArraySize(long[] o, LocationStack stack, int index) {
		IndexMap map = getDetailedIndex(stack, index);
		int size = getArraySize(o);
		map.increment(o.length, size);
	}

	public static void allocateArraySize(float[] o, LocationStack stack, int index) {
		IndexMap map = getDetailedIndex(stack, index);
		int size = getArraySize(o);
		map.increment(o.length, size);
	}

	public static void allocateArraySize(double[] o, LocationStack stack, int index) {
		IndexMap map = getDetailedIndex(stack, index);
		int size = getArraySize(o);
		map.increment(o.length, size);
	}

	public static void allocateArraySize(Object[] o, LocationStack stack, int index) {
		IndexMap map = getDetailedIndex(stack, index);
		int size = getArraySize(o);
		map.increment(o.length, size);
	}

	public static void allocateArraySizeMulti(Object[] o, LocationStack stack, int index) {
		IndexMap map = getDetailedIndex(stack, index);
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

	public static void allocateReflectVClone(Object o, int reflectIndex) {
		if (isDirectCloneClass(o.getClass().getName()))
			allocateReflect(o, reflectIndex);
	}

	public static void allocateReflectVCloneSize(Object o, int reflectIndex) {
		if (isDirectCloneClass(o.getClass().getName()))
			allocateReflectSize(o, reflectIndex);
	}

	public static void objectInit(Object o) {
		String cname = o.getClass().getName();
		DatatypeInfo datatypeInfo = getDatatypeInfo(cname);
		if (datatypeInfo == null)
			return;
		datatypeInfo.getIndex().increment();
	}

	public static void objectInitSize(Object o) {
		String cname = o.getClass().getName();
		DatatypeInfo datatypeInfo = getDatatypeInfo(cname);
		if (datatypeInfo == null)
			return;
		if (datatypeInfo.getSize() == 0)
			datatypeInfo.setSize(getObjectSize(o));
		datatypeInfo.getIndex().increment();
	}
}
