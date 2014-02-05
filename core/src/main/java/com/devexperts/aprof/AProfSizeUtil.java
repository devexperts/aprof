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

import java.lang.instrument.Instrumentation;

import com.devexperts.aprof.util.UnsafeHolder;

/**
 * @author Dmitry Paraschenko
 */
public class AProfSizeUtil {
	private static final int SIZE_CACHE = 1024;

	private static final long[] booleanSizeCache = new long[SIZE_CACHE];
	private static final long[] byteSizeCache = new long[SIZE_CACHE];
	private static final long[] charSizeCache = new long[SIZE_CACHE];
	private static final long[] shortSizeCache = new long[SIZE_CACHE];
	private static final long[] intSizeCache = new long[SIZE_CACHE];
	private static final long[] longSizeCache = new long[SIZE_CACHE];
	private static final long[] floatSizeCache = new long[SIZE_CACHE];
	private static final long[] doubleSizeCache = new long[SIZE_CACHE];
	private static final long[] objectSizeCache = new long[SIZE_CACHE];

	private static Instrumentation inst;

	private AProfSizeUtil() {} // do not create

	public static void init(Instrumentation instrumentation) {
		if (inst != null)
			throw new IllegalStateException("AProfSizeUtil is already initialized");
		inst = instrumentation;
	}

	public static long getArraySize(boolean[] o) {
		long size;
		if (o.length < SIZE_CACHE) {
			size = booleanSizeCache[o.length];
			if (size == 0)
				booleanSizeCache[o.length] = size = getObjectSize(o);
		} else
			size = getObjectSize(o);
		return size;
	}

	public static long getArraySize(byte[] o) {
		long size;
		if (o.length < SIZE_CACHE) {
			size = byteSizeCache[o.length];
			if (size == 0)
				byteSizeCache[o.length] = size = getObjectSize(o);
		} else
			size = getObjectSize(o);
		return size;
	}

	public static long getArraySize(char[] o) {
		if (o.length < SIZE_CACHE) {
			long size = charSizeCache[o.length];
			if (size == 0)
				charSizeCache[o.length] = size = getObjectSize(o);
			return size;
		} else
			return getObjectSize(o);
	}

	public static long getArraySize(short[] o) {
		long size;
		if (o.length < SIZE_CACHE) {
			size = shortSizeCache[o.length];
			if (size == 0)
				shortSizeCache[o.length] = size = getObjectSize(o);
		} else
			size = getObjectSize(o);
		return size;
	}

	public static long getArraySize(int[] o) {
		long size;
		if (o.length < SIZE_CACHE) {
			size = intSizeCache[o.length];
			if (size == 0)
				intSizeCache[o.length] = size = getObjectSize(o);
		} else
			size = getObjectSize(o);
		return size;
	}

	public static long getArraySize(long[] o) {
		long size;
		if (o.length < SIZE_CACHE) {
			size = longSizeCache[o.length];
			if (size == 0)
				longSizeCache[o.length] = size = getObjectSize(o);
		} else
			size = getObjectSize(o);
		return size;
	}

	public static long getArraySize(float[] o) {
		long size;
		if (o.length < SIZE_CACHE) {
			size = floatSizeCache[o.length];
			if (size == 0)
				floatSizeCache[o.length] = size = getObjectSize(o);
		} else
			size = getObjectSize(o);
		return size;
	}

	public static long getArraySize(double[] o) {
		long size;
		if (o.length < SIZE_CACHE) {
			size = doubleSizeCache[o.length];
			if (size == 0)
				doubleSizeCache[o.length] = size = getObjectSize(o);
		} else
			size = getObjectSize(o);
		return size;
	}

	public static long getArraySize(Object[] o) {
		if (o.length < SIZE_CACHE) {
			long size = objectSizeCache[o.length];
			if (size == 0)
				objectSizeCache[o.length] = size = getObjectSize(o);
			return size;
		} else
			return getObjectSize(o);
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
			return getArraySize((char[])o);
		else if (o instanceof byte[])
			return getArraySize((byte[])o);
		else if (o instanceof short[])
			return getArraySize((short[])o);
		else if (o instanceof int[])
			return getArraySize((int[])o);
		else if (o instanceof long[])
			return getArraySize((long[])o);
		else if (o instanceof boolean[])
			return getArraySize((boolean[])o);
		else if (o instanceof float[])
			return getArraySize((float[])o);
		else if (o instanceof double[])
			return getArraySize((double[])o);
		else
			return 0;
	}

	public static long getArraySizeMultiRec(Object[] o) {
		long size = getArraySize(o);
		if (o.getClass().getComponentType().isArray() && o.length > 0)
			size += o.length * getArraySizeMultiRec(o[0]);
		return size;
	}
}
