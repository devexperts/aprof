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

import java.lang.reflect.Array;
import java.io.UnsupportedEncodingException;

/**
 * @author Roman Elizarov
 */
public class AProfTemplate implements Cloneable {
	public AProfTemplate() {
		AProfOps.objectInitSize(this);
	}

	public Integer template() {
		int[][] multi = new int[10][15];
		AProfOps.allocateArraySizeMulti(multi, 56);
		int[][] multi2 = multi.clone();
		AProfOps.allocateArraySize(multi2, 55);
		Object o1 = Array.newInstance(byte.class, 10);
		AProfOps.allocateReflect(o1, 78);
		Object o2 = Array.newInstance(AProfTemplate.class, new int[] {10});
		AProfOps.allocateReflectSize(o2, 89);
		Object[] a = new Object[64];
		AProfOps.allocateArraySize(a, 1);
		AProfOps.allocate(12);
		AProfOps.allocate(1234);
		AProfOps.allocate(123456);
		//noinspection UnnecessaryBoxing
		return new Integer(5678);
	}

	public String buffers(int i) {
		new StringBuffer().append(i).toString();
		new StringBuilder().append(i).toString();
		return "test" + i;
	}

	public String string() throws UnsupportedEncodingException {
		return new String("bytes".getBytes(), 0, 0, "UTF-8");
	}

	public AProfTemplate dup1() {
		try {
			return (AProfTemplate)super.clone();
		} catch (CloneNotSupportedException e) {
			throw new InternalError();
		}
	}

	public AProfTemplate dup2() {
		try {
			return (AProfTemplate)clone();
		} catch (CloneNotSupportedException e) {
			throw new InternalError();
		}
	}
}
