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

import static com.devexperts.aprof.AProfRegistry.getRootIndex;
import static com.devexperts.aprof.AProfSizeUtil.*;

/**
 * Methods that are instrumented into "internal" target code by aprof method transformer.
 * The difference is that they don't use {@link LocationStack} parameter.
 */
@SuppressWarnings({"UnusedDeclaration"})
@Internal
public class AProfOpsInternal extends AProfOps {
	public static void allocate(LocationStack stack, int index) {
		getRootIndex(index).incrementCount();
	}

	public static void allocateSize(LocationStack stack, int index, Class objectClass) {
		RootIndexMap rootIndex = getRootIndex(index);
		rootIndex.incrementCount();
		DatatypeInfo datatypeInfo = rootIndex.getDatatypeInfo();
		if (datatypeInfo.getSize() == 0)
			datatypeInfo.setSize(getObjectSizeByClass(objectClass));
	}

	public static void booleanAllocateArraySize(int length, LocationStack stack, int index) {
		if (length < 0)
			return; // will throw NegativeArraySizeException instead of array allocation
		IndexMap map = getRootIndex(index);
		map.incrementArraySizeAndCount(length, booleanArraySize(length));
	}

	public static void byteAllocateArraySize(int length, LocationStack stack, int index) {
		if (length < 0)
			return; // will throw NegativeArraySizeException instead of array allocation
		IndexMap map = getRootIndex(index);
		map.incrementArraySizeAndCount(length, byteArraySize(length));
	}

	public static void charAllocateArraySize(int length, LocationStack stack, int index) {
		if (length < 0)
			return; // will throw NegativeArraySizeException instead of array allocation
		IndexMap map = getRootIndex(index);
		map.incrementArraySizeAndCount(length, charArraySize(length));
	}

	public static void shortAllocateArraySize(int length, LocationStack stack, int index) {
		if (length < 0)
			return; // will throw NegativeArraySizeException instead of array allocation
		IndexMap map = getRootIndex(index);
		map.incrementArraySizeAndCount(length, shortArraySize(length));
	}

	public static void intAllocateArraySize(int length, LocationStack stack, int index) {
		if (length < 0)
			return; // will throw NegativeArraySizeException instead of array allocation
		IndexMap map = getRootIndex(index);
		map.incrementArraySizeAndCount(length, intArraySize(length));
	}

	public static void longAllocateArraySize(int length, LocationStack stack, int index) {
		if (length < 0)
			return; // will throw NegativeArraySizeException instead of array allocation
		IndexMap map = getRootIndex(index);
		map.incrementArraySizeAndCount(length, longArraySize(length));
	}

	public static void floatAllocateArraySize(int length, LocationStack stack, int index) {
		if (length < 0)
			return; // will throw NegativeArraySizeException instead of array allocation
		IndexMap map = getRootIndex(index);
		map.incrementArraySizeAndCount(length, floatArraySize(length));
	}

	public static void doubleAllocateArraySize(int length, LocationStack stack, int index) {
		if (length < 0)
			return; // will throw NegativeArraySizeException instead of array allocation
		IndexMap map = getRootIndex(index);
		map.incrementArraySizeAndCount(length, doubleArraySize(length));
	}

	public static void objectAllocateArraySize(int length, LocationStack stack, int index) {
		if (length < 0)
			return; // will throw NegativeArraySizeException instead of array allocation
		IndexMap map = getRootIndex(index);
		map.incrementArraySizeAndCount(length, objectArraySize(length));
	}

	public static void allocateArraySizeMulti(Object[] o, LocationStack stack, int index) {
		IndexMap map = getRootIndex(index);
		map.incrementArraySizeAndCount(o.length, getArraySizeMultiRec(o));
	}
}
