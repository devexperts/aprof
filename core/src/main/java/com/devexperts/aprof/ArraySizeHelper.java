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

import java.lang.instrument.Instrumentation;

/**
 * @author Dmitry Paraschenko
 */
class ArraySizeHelper {
	public static final int SIZE_SHIFT = 3;

	private static final int SIZE_CACHE = 1024;

	private static final int[] boolean_szc = new int[SIZE_CACHE];
	private static final int[] byte_szc = new int[SIZE_CACHE];
	private static final int[] char_szc = new int[SIZE_CACHE];
	private static final int[] short_szc = new int[SIZE_CACHE];
	private static final int[] int_szc = new int[SIZE_CACHE];
	private static final int[] long_szc = new int[SIZE_CACHE];
	private static final int[] float_szc = new int[SIZE_CACHE];
	private static final int[] double_szc = new int[SIZE_CACHE];
	private static final int[] object_szc = new int[SIZE_CACHE];

	private static Instrumentation inst;

	public static void init(Instrumentation instrumentation) {
		if (inst != null)
			throw new IllegalStateException("ArraySizeHelper is already initialized");
		inst = instrumentation;
	}

	public static int getArraySize(boolean[] o) {
		int size;
		if (o.length < SIZE_CACHE) {
			size = boolean_szc[o.length];
			if (size == 0)
				boolean_szc[o.length] = size = getObjectSize(o);
		} else
			size = getObjectSize(o);
		return size;
	}

	public static int getArraySize(byte[] o) {
		int size;
		if (o.length < SIZE_CACHE) {
			size = byte_szc[o.length];
			if (size == 0)
				byte_szc[o.length] = size = getObjectSize(o);
		} else
			size = getObjectSize(o);
		return size;
	}

	public static int getArraySize(char[] o) {
		if (o.length < SIZE_CACHE) {
			int size = char_szc[o.length];
			if (size == 0)
				char_szc[o.length] = size = getObjectSize(o);
			return size;
		} else
			return getObjectSize(o);
	}

	public static int getArraySize(short[] o) {
		int size;
		if (o.length < SIZE_CACHE) {
			size = short_szc[o.length];
			if (size == 0)
				short_szc[o.length] = size = getObjectSize(o);
		} else
			size = getObjectSize(o);
		return size;
	}

	public static int getArraySize(int[] o) {
		int size;
		if (o.length < SIZE_CACHE) {
			size = int_szc[o.length];
			if (size == 0)
				int_szc[o.length] = size = getObjectSize(o);
		} else
			size = getObjectSize(o);
		return size;
	}

	public static int getArraySize(long[] o) {
		int size;
		if (o.length < SIZE_CACHE) {
			size = long_szc[o.length];
			if (size == 0)
				long_szc[o.length] = size = getObjectSize(o);
		} else
			size = getObjectSize(o);
		return size;
	}

	public static int getArraySize(float[] o) {
		int size;
		if (o.length < SIZE_CACHE) {
			size = float_szc[o.length];
			if (size == 0)
				float_szc[o.length] = size = getObjectSize(o);
		} else
			size = getObjectSize(o);
		return size;
	}

	public static int getArraySize(double[] o) {
		int size;
		if (o.length < SIZE_CACHE) {
			size = double_szc[o.length];
			if (size == 0)
				double_szc[o.length] = size = getObjectSize(o);
		} else
			size = getObjectSize(o);
		return size;
	}

	public static int getArraySize(Object[] o) {
		if (o.length < SIZE_CACHE) {
			int size = object_szc[o.length];
			if (size == 0)
				object_szc[o.length] = size = getObjectSize(o);
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
