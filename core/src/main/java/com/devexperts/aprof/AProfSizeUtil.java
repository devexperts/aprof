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

import java.lang.instrument.Instrumentation;

import com.devexperts.aprof.util.UnsafeHolder;

public class AProfSizeUtil {
	private AProfSizeUtil() {} // do not create

	private static long BOOLEAN_BASE_OFFSET = UnsafeHolder.UNSAFE.arrayBaseOffset(boolean[].class);
	private static long BOOLEAN_INDEX_SCALE = UnsafeHolder.UNSAFE.arrayIndexScale(boolean[].class);
	private static long BYTE_BASE_OFFSET = UnsafeHolder.UNSAFE.arrayBaseOffset(byte[].class);
	private static long BYTE_INDEX_SCALE = UnsafeHolder.UNSAFE.arrayIndexScale(byte[].class);
	private static long CHAR_BASE_OFFSET = UnsafeHolder.UNSAFE.arrayBaseOffset(char[].class);
	private static long CHAR_INDEX_SCALE = UnsafeHolder.UNSAFE.arrayIndexScale(char[].class);
	private static long SHORT_BASE_OFFSET = UnsafeHolder.UNSAFE.arrayBaseOffset(short[].class);
	private static long SHORT_INDEX_SCALE = UnsafeHolder.UNSAFE.arrayIndexScale(short[].class);
	private static long INT_BASE_OFFSET = UnsafeHolder.UNSAFE.arrayBaseOffset(int[].class);
	private static long INT_INDEX_SCALE = UnsafeHolder.UNSAFE.arrayIndexScale(int[].class);
	private static long LONG_BASE_OFFSET = UnsafeHolder.UNSAFE.arrayBaseOffset(long[].class);
	private static long LONG_INDEX_SCALE = UnsafeHolder.UNSAFE.arrayIndexScale(long[].class);
	private static long FLOAT_BASE_OFFSET = UnsafeHolder.UNSAFE.arrayBaseOffset(float[].class);
	private static long FLOAT_INDEX_SCALE = UnsafeHolder.UNSAFE.arrayIndexScale(float[].class);
	private static long DOUBLE_BASE_OFFSET = UnsafeHolder.UNSAFE.arrayBaseOffset(double[].class);
	private static long DOUBLE_INDEX_SCALE = UnsafeHolder.UNSAFE.arrayIndexScale(double[].class);
	private static long OBJECT_BASE_OFFSET = UnsafeHolder.UNSAFE.arrayBaseOffset(Object[].class);
	private static long OBJECT_INDEX_SCALE = UnsafeHolder.UNSAFE.arrayIndexScale(Object[].class);

	private static Instrumentation inst;

	public static void init(Instrumentation instrumentation) {
		if (inst != null)
			throw new IllegalStateException("AProfSizeUtil is already initialized");
		inst = instrumentation;
	}

	private static long align(long size) {
		return (size + 7) & ~7L;
	}

	public static long booleanArraySize(int length) {
		return align(BOOLEAN_BASE_OFFSET + BOOLEAN_INDEX_SCALE * length);
	}

	public static long byteArraySize(int length) {
		return align(BYTE_BASE_OFFSET + BYTE_INDEX_SCALE * length);
	}

	public static long charArraySize(int length) {
		return align(CHAR_BASE_OFFSET + CHAR_INDEX_SCALE * length);
	}

	public static long shortArraySize(int length) {
		return align(SHORT_BASE_OFFSET + SHORT_INDEX_SCALE * length);
	}

	public static long intArraySize(int length) {
		return align(INT_BASE_OFFSET + INT_INDEX_SCALE * length);
	}

	public static long longArraySize(int length) {
		return align(LONG_BASE_OFFSET + LONG_INDEX_SCALE * length);
	}

	public static long floatArraySize(int length) {
		return align(FLOAT_BASE_OFFSET + FLOAT_INDEX_SCALE * length);
	}

	public static long doubleArraySize(int length) {
		return align(DOUBLE_BASE_OFFSET + DOUBLE_INDEX_SCALE * length);
	}

	public static long objectArraySize(int length) {
		return align(OBJECT_BASE_OFFSET + OBJECT_INDEX_SCALE * length);
	}

	public static long getObjectSize(Object o) {
		return inst.getObjectSize(o);
	}

	public static long getObjectSizeByClass(Class objectClass) {
		long size;
		try {
			size = getObjectSize(UnsafeHolder.UNSAFE.allocateInstance(objectClass));
		} catch (InstantiationException e) {
			size = -1;
		}
		return size;
	}

	public static long getArraySizeMultiRec(Object o) {
		if (o instanceof Object[])
			return getArraySizeMultiRec((Object[])o);
		else if (o instanceof char[])
			return charArraySize(((char[])o).length);
		else if (o instanceof byte[])
			return byteArraySize(((byte[])o).length);
		else if (o instanceof short[])
			return shortArraySize(((short[])o).length);
		else if (o instanceof int[])
			return intArraySize(((int[])o).length);
		else if (o instanceof long[])
			return longArraySize(((long[])o).length);
		else if (o instanceof boolean[])
			return booleanArraySize(((boolean[])o).length);
		else if (o instanceof float[])
			return floatArraySize(((float[])o).length);
		else if (o instanceof double[])
			return doubleArraySize(((double[])o).length);
		else
			return 0;
	}

	public static long getArraySizeMultiRec(Object[] o) {
		int length = o.length;
		long size = objectArraySize(length);
		if (o.getClass().getComponentType().isArray() && length > 0)
			size += length * getArraySizeMultiRec(o[0]);
		return size;
	}
}
