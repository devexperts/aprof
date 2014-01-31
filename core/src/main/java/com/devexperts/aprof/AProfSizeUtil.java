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

/**
 * @author Dmitry Paraschenko
 */
public class AProfSizeUtil {
	public static final int SIZE_SHIFT = 3;

	private static final int SIZE_CACHE = 1024;

	private static final int[] booleanSizeCache = new int[SIZE_CACHE];
	private static final int[] byteSizeCache = new int[SIZE_CACHE];
	private static final int[] charSizeCache = new int[SIZE_CACHE];
	private static final int[] shortSizeCache = new int[SIZE_CACHE];
	private static final int[] intSizeCache = new int[SIZE_CACHE];
	private static final int[] longSizeCache = new int[SIZE_CACHE];
	private static final int[] floatSizeCache = new int[SIZE_CACHE];
	private static final int[] doubleSizeCache = new int[SIZE_CACHE];
	private static final int[] objectSizeCache = new int[SIZE_CACHE];

	private static Instrumentation inst;

	private AProfSizeUtil() {} // do not create

	public static void init(Instrumentation instrumentation) {
		if (inst != null)
			throw new IllegalStateException("AProfSizeUtil is already initialized");
		inst = instrumentation;
	}

	public static int getArraySize(boolean[] o) {
		int size;
		if (o.length < SIZE_CACHE) {
			size = booleanSizeCache[o.length];
			if (size == 0)
				booleanSizeCache[o.length] = size = getObjectSize(o);
		} else
			size = getObjectSize(o);
		return size;
	}

	public static int getArraySize(byte[] o) {
		int size;
		if (o.length < SIZE_CACHE) {
			size = byteSizeCache[o.length];
			if (size == 0)
				byteSizeCache[o.length] = size = getObjectSize(o);
		} else
			size = getObjectSize(o);
		return size;
	}

	public static int getArraySize(char[] o) {
		if (o.length < SIZE_CACHE) {
			int size = charSizeCache[o.length];
			if (size == 0)
				charSizeCache[o.length] = size = getObjectSize(o);
			return size;
		} else
			return getObjectSize(o);
	}

	public static int getArraySize(short[] o) {
		int size;
		if (o.length < SIZE_CACHE) {
			size = shortSizeCache[o.length];
			if (size == 0)
				shortSizeCache[o.length] = size = getObjectSize(o);
		} else
			size = getObjectSize(o);
		return size;
	}

	public static int getArraySize(int[] o) {
		int size;
		if (o.length < SIZE_CACHE) {
			size = intSizeCache[o.length];
			if (size == 0)
				intSizeCache[o.length] = size = getObjectSize(o);
		} else
			size = getObjectSize(o);
		return size;
	}

	public static int getArraySize(long[] o) {
		int size;
		if (o.length < SIZE_CACHE) {
			size = longSizeCache[o.length];
			if (size == 0)
				longSizeCache[o.length] = size = getObjectSize(o);
		} else
			size = getObjectSize(o);
		return size;
	}

	public static int getArraySize(float[] o) {
		int size;
		if (o.length < SIZE_CACHE) {
			size = floatSizeCache[o.length];
			if (size == 0)
				floatSizeCache[o.length] = size = getObjectSize(o);
		} else
			size = getObjectSize(o);
		return size;
	}

	public static int getArraySize(double[] o) {
		int size;
		if (o.length < SIZE_CACHE) {
			size = doubleSizeCache[o.length];
			if (size == 0)
				doubleSizeCache[o.length] = size = getObjectSize(o);
		} else
			size = getObjectSize(o);
		return size;
	}

	public static int getArraySize(Object[] o) {
		if (o.length < SIZE_CACHE) {
			int size = objectSizeCache[o.length];
			if (size == 0)
				objectSizeCache[o.length] = size = getObjectSize(o);
			return size;
		} else
			return getObjectSize(o);
	}

	public static int getObjectSize(Object o) {
		return (int)(inst.getObjectSize(o) >> SIZE_SHIFT);
	}

	public static int getArraySizeMultiRec(Object o) {
		if (o instanceof Object[]) {
			return getArraySizeMultiRec((Object[])o);
		} else if (o instanceof char[]) {
			return getArraySize((char[])o);
		} else if (o != null) {
			return getObjectSize(o);
		} else {
			return 0;
		}
	}

	public static int getArraySizeMultiRec(Object[] o) {
		int size = getArraySize(o);
		if (o.getClass().getComponentType().isArray() && o.length > 0)
			size += o.length * getArraySizeMultiRec(o[0]);
		return size;
	}
}
