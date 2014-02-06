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

package com.devexperts.aprof.benchmark;

import org.openjdk.jmh.annotations.GenerateMicroBenchmark;

public class BenchmarkEscapeAnalysis {
	private static final Box BOX = newBox();
	private static int[] ARRAY = newArray();

	private static Box newBox() {
		return new Box(1);
	}

	private static Box cloneBox() {
		return BOX.clone();
	}

	private static int[] newArray() {
		return new int[] { 1 };
	}

	private static int[] cloneArray() {
		return ARRAY.clone();
	}

	@GenerateMicroBenchmark
	public int testNewBox() {
		return newBox().getValue();
	}

	@GenerateMicroBenchmark
	public int testCloneBox() {
		return cloneBox().getValue();
	}

	@GenerateMicroBenchmark
	public int testNewArray() {
		return newArray()[0];
	}

	@GenerateMicroBenchmark
	public int testCloneArray() {
		return cloneArray()[0];
	}

	private static class Box implements Cloneable {
		private int value;

		Box(int value) {
			this.value = value;
		}

		@Override
		public Box clone() {
			try {
				return (Box)super.clone();
			} catch (CloneNotSupportedException e) {
				throw new AssertionError(e);
			}
		}

		private int getValue() {
			return value;
		}
	}
}
