/*
 * Aprof - Java Memory Allocation Profiler
 * Copyright (C) 2002-2013  Devexperts LLC
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

import java.awt.*;
import java.io.*;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.nio.CharBuffer;

/**
 * @author Roman Elizarov
 */
public class AProfTarget {
	private static final char[] CHARS = "chars".toCharArray();
	private static final byte[] BYTES = "bytes".getBytes();
	private static final int[] CODE_POINTS = new int[] { CHARS[0], CHARS[1], CHARS[2], CHARS[3], CHARS[4] };

	public static void main(String[] args) throws Exception {
		// all ways to create string
		createStrings();
		// 4 x Dupable instances
		cloneObjectSerial(copyObjectReflect(dupeDupable(allocateDupable())));
		// 4 x Point instances
		cloneObjectSerial(copyObjectReflect(clonePoint(allocatePoint())));
		// 4 x Point[] instances
		cloneObjectSerial(cloneArrayReflect(clonePointArray(allocatePointArray())));
		// 4 x short[] instances
		cloneObjectSerial(cloneArrayReflect(cloneShortArray(allocateShortArray())));
	}

	private static String[] createStrings() throws UnsupportedEncodingException {
		return new String[] {
			new String(),
			new String("original"),
			new String(CHARS),
			new String(CHARS, 0, CHARS.length),
			new String(CODE_POINTS, 0, CODE_POINTS.length),
			new String(BYTES, 0, 0, BYTES.length),
			new String(BYTES, 0),
			new String(BYTES, 0, BYTES.length, "UTF-8"),
			new String(BYTES, "UTF-8"),
			new String(BYTES, 0, BYTES.length),
			new String(BYTES),
			new String(new StringBuffer().append(1)),
			new String(new StringBuilder().append(1)),
			new String(new StringBuffer(10).append(1)),
			new String(new StringBuilder(10).append(1)),
			new StringBuffer().append(1).toString(),
			new StringBuilder().append(1).toString(),
			new StringBuffer("str").toString(),
			new StringBuilder("str").toString(),
			new StringBuffer(CharBuffer.wrap(CHARS)).toString(),
			new StringBuilder(CharBuffer.wrap(CHARS)).toString(),
			String.valueOf(new StringBuffer("str")),
			String.valueOf(new StringBuilder("str")),
			Integer.toString(1),
			String.valueOf(1)
		};
	}

	private static DupableObject allocateDupable() {
		return new DupableObject(null);
	}

	private static DupableObject dupeDupable(DupableObject d) {
		return d.dup();
	}

	private static Point allocatePoint() {
		return new Point(100, 200);
	}

	private static Point clonePoint(Point p) {
		return (Point)p.clone();
	}

	private static Object copyObjectReflect(Object o) throws NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException {
		return o.getClass().getConstructor(o.getClass()).newInstance(o);
	}

	private static Object cloneObjectSerial(Object o) throws IOException, ClassNotFoundException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		ObjectOutputStream oos = new ObjectOutputStream(baos);
		oos.writeObject(o);
		ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
		ObjectInputStream ois = new ObjectInputStream(bais);
		return ois.readObject();
	}

	private static Point[] allocatePointArray() {
		return new Point[10];
	}

	private static Point[] clonePointArray(Point[] a) {
		return a.clone();
	}

	private static short[] allocateShortArray() {
		return new short[10];
	}

	private static short[] cloneShortArray(short[] a) {
		return a.clone();
	}

	private static Object cloneArrayReflect(Object o) {
		return Array.newInstance(o.getClass().getComponentType(), Array.getLength(o));
	}
}
