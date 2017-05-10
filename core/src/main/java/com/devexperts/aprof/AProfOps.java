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

	public static void booleanAllocateArraySize(int length, LocationStack stack, int index) {
		if (length < 0)
			return; // will throw NegativeArraySizeException instead of array allocation
		IndexMap map = getDetailedIndex(stack, getRootIndex(index));
		map.incrementArraySizeAndCount(length, booleanArraySize(length));
	}

	public static void byteAllocateArraySize(int length, LocationStack stack, int index) {
		if (length < 0)
			return; // will throw NegativeArraySizeException instead of array allocation
		IndexMap map = getDetailedIndex(stack, getRootIndex(index));
		map.incrementArraySizeAndCount(length, byteArraySize(length));
	}

	public static void charAllocateArraySize(int length, LocationStack stack, int index) {
		if (length < 0)
			return; // will throw NegativeArraySizeException instead of array allocation
		IndexMap map = getDetailedIndex(stack, getRootIndex(index));
		map.incrementArraySizeAndCount(length, charArraySize(length));
	}

	public static void shortAllocateArraySize(int length, LocationStack stack, int index) {
		if (length < 0)
			return; // will throw NegativeArraySizeException instead of array allocation
		IndexMap map = getDetailedIndex(stack, getRootIndex(index));
		map.incrementArraySizeAndCount(length, shortArraySize(length));
	}

	public static void intAllocateArraySize(int length, LocationStack stack, int index) {
		if (length < 0)
			return; // will throw NegativeArraySizeException instead of array allocation
		IndexMap map = getDetailedIndex(stack, getRootIndex(index));
		map.incrementArraySizeAndCount(length, intArraySize(length));
	}

	public static void longAllocateArraySize(int length, LocationStack stack, int index) {
		if (length < 0)
			return; // will throw NegativeArraySizeException instead of array allocation
		IndexMap map = getDetailedIndex(stack, getRootIndex(index));
		map.incrementArraySizeAndCount(length, longArraySize(length));
	}

	public static void floatAllocateArraySize(int length, LocationStack stack, int index) {
		if (length < 0)
			return; // will throw NegativeArraySizeException instead of array allocation
		IndexMap map = getDetailedIndex(stack, getRootIndex(index));
		map.incrementArraySizeAndCount(length, floatArraySize(length));
	}

	public static void doubleAllocateArraySize(int length, LocationStack stack, int index) {
		if (length < 0)
			return; // will throw NegativeArraySizeException instead of array allocation
		IndexMap map = getDetailedIndex(stack, getRootIndex(index));
		map.incrementArraySizeAndCount(length, doubleArraySize(length));
	}

	public static void objectAllocateArraySize(int length, LocationStack stack, int index) {
		if (length < 0)
			return; // will throw NegativeArraySizeException instead of array allocation
		IndexMap map = getDetailedIndex(stack, getRootIndex(index));
		map.incrementArraySizeAndCount(length, objectArraySize(length));
	}

	public static void allocateArraySizeMulti(Object[] o, LocationStack stack, int index) {
		IndexMap map = getDetailedIndex(stack, getRootIndex(index));
		map.incrementArraySizeAndCount(o.length, getArraySizeMultiRec(o));
	}

	public static void allocateReflect(Object o, LocationStack stack, int index) {
		IndexMap map = getDetailedIndex(stack, getRootIndex(AProfRegistry.resolveClassName(o.getClass().getName()), index));
		map.incrementCount();
	}

	public static void allocateReflectSize(Object o, LocationStack stack, int index) {
		RootIndexMap rootIndex = getRootIndex(AProfRegistry.resolveClassName(o.getClass().getName()), index);
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
