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

import java.lang.reflect.Array;

import static com.devexperts.aprof.AProfRegistry.*;
import static com.devexperts.aprof.AProfSizeUtil.*;

/**
 * Methods that are instrumented into target code by aprof method transformer.
 */
@SuppressWarnings({"UnusedDeclaration"})
@Internal
public class AProfOps {
	public static void allocate(LocationStack stack, int index) {
		getDetailedIndex(stack, getRootIndex(index)).incrementCount();
	}

	public static void allocateSize(LocationStack stack, int index, Class objectClass) {
		RootIndexMap rootIndex = getRootIndex(index);
		getDetailedIndex(stack, rootIndex).incrementCount();
		DatatypeInfo datatypeInfo = rootIndex.getDatatypeInfo();
		if (datatypeInfo.getSize() == 0)
			datatypeInfo.setSize(getObjectSizeByClass(objectClass));
	}

	public static void allocateArraySize(boolean[] o, LocationStack stack, int index) {
		IndexMap map = getDetailedIndex(stack, getRootIndex(index));
		map.incrementArraySizeAndCount(o.length, getArraySize(o));
	}

	public static void allocateArraySize(byte[] o, LocationStack stack, int index) {
		IndexMap map = getDetailedIndex(stack, getRootIndex(index));
		map.incrementArraySizeAndCount(o.length, getArraySize(o));
	}

	public static void allocateArraySize(char[] o, LocationStack stack, int index) {
		IndexMap map = getDetailedIndex(stack, getRootIndex(index));
		map.incrementArraySizeAndCount(o.length, getArraySize(o));
	}

	public static void allocateArraySize(short[] o, LocationStack stack, int index) {
		IndexMap map = getDetailedIndex(stack, getRootIndex(index));
		map.incrementArraySizeAndCount(o.length, getArraySize(o));
	}

	public static void allocateArraySize(int[] o, LocationStack stack, int index) {
		IndexMap map = getDetailedIndex(stack, getRootIndex(index));
		map.incrementArraySizeAndCount(o.length, getArraySize(o));
	}

	public static void allocateArraySize(long[] o, LocationStack stack, int index) {
		IndexMap map = getDetailedIndex(stack, getRootIndex(index));
		map.incrementArraySizeAndCount(o.length, getArraySize(o));
	}

	public static void allocateArraySize(float[] o, LocationStack stack, int index) {
		IndexMap map = getDetailedIndex(stack, getRootIndex(index));
		map.incrementArraySizeAndCount(o.length, getArraySize(o));
	}

	public static void allocateArraySize(double[] o, LocationStack stack, int index) {
		IndexMap map = getDetailedIndex(stack, getRootIndex(index));
		map.incrementArraySizeAndCount(o.length, getArraySize(o));
	}

	public static void allocateArraySize(Object[] o, LocationStack stack, int index) {
		IndexMap map = getDetailedIndex(stack, getRootIndex(index));
		map.incrementArraySizeAndCount(o.length, getArraySize(o));
	}

	public static void allocateArraySizeMulti(Object[] o, LocationStack stack, int index) {
		IndexMap map = getDetailedIndex(stack, getRootIndex(index));
		map.incrementArraySizeAndCount(o.length, getArraySizeMultiRec(o));
	}

	public static void allocateReflect(Object o, LocationStack stack, int index) {
		IndexMap map = getDetailedIndex(stack, getRootIndex(o.getClass().getName(), index));
		map.incrementCount();
	}

	public static void allocateReflectSize(Object o, LocationStack stack, int index) {
		RootIndexMap rootIndex = getRootIndex(o.getClass().getName(), index);
		DatatypeInfo datatypeInfo = rootIndex.getDatatypeInfo();
		IndexMap map = getDetailedIndex(stack, rootIndex);
		if (datatypeInfo.isArray()) {
			map.incrementArraySizeAndCount(Array.getLength(o), getArraySizeMultiRec(o));
		} else {
			map.incrementCount();
			if (datatypeInfo.getSize() == 0)
				datatypeInfo.setSize(getObjectSize(o));
		}
	}

	public static void allocateReflectVClone(Object o, LocationStack stack, int index) {
		if (isDirectCloneClass(o.getClass().getName()))
			allocateReflect(o, stack, index);
	}

	public static void allocateReflectVCloneSize(Object o, LocationStack stack, int index) {
		if (isDirectCloneClass(o.getClass().getName()))
			allocateReflectSize(o, stack, index);
	}

	public static void objectInit(Object o) {
		String name = o.getClass().getName();
		DatatypeInfo datatypeInfo = getDatatypeInfo(name);
		if (datatypeInfo == null)
			return;
		datatypeInfo.getIndex().incrementCount();
	}

	public static void objectInitSize(Object o) {
		DatatypeInfo datatypeInfo = getDatatypeInfo(o.getClass().getName());
		if (datatypeInfo == null)
			return;
		datatypeInfo.getIndex().incrementCount();
		if (datatypeInfo.getSize() == 0)
			datatypeInfo.setSize(getObjectSize(o));
	}
}
