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

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author Roman Elizarov
 */
public class MicroPerfTest {
	private static final int COUNT = 100000000;
	private static final String MFT = "%15s %10d ms\n";

	public static void main(String[] args) {
		int dummy = 0;
		for (int i = 1; i <= 3; i++) {
			System.out.println("Pass #" + i);
			dummy += runTestAll();
		}
		System.exit(dummy);
	}

	private static int runTestAll() {
		int res = 0;
		res += test1();
		res += test2();
		res += test3();
		res += test4();
		res += test5();
		res += test6();
		return res;
	}

	private static int test1() {
		long nanos = System.nanoTime();
		int i = 0;
		for (int k = 0; k < COUNT; k++)
			i++;
		nanos = System.nanoTime() - nanos;
		System.out.format(MFT, "int++", nanos);
		return i;
	}

	private static int test2() {
		long nanos = System.nanoTime();
		long i = 0;
		for (int k = 0; k < COUNT; k++)
			i++;
		nanos = System.nanoTime() - nanos;
		System.out.format(MFT, "long++", nanos);
		return (int)i;
	}

	private static int test3() {
		long nanos = System.nanoTime();
		AtomicInteger i = new AtomicInteger();
		for (int k = 0; k < COUNT; k++)
			i.getAndIncrement();
		nanos = System.nanoTime() - nanos;
		System.out.format(MFT, "atomic int++", nanos);
		return i.get();
	}

	private static int test4() {
		long nanos = System.nanoTime();
		AtomicLong i = new AtomicLong();
		for (int k = 0; k < COUNT; k++)
			i.getAndIncrement();
		nanos = System.nanoTime() - nanos;
		System.out.format(MFT, "atomic long++", nanos);
		return (int)i.get();
	}

	private static int test5() {
		long nanos = System.nanoTime();
		AtomicInteger i = new AtomicInteger();
		for (int k = 0; k < COUNT; k++)
			i.incrementAndGet();
		nanos = System.nanoTime() - nanos;
		System.out.format(MFT, "++atomic int", nanos);
		return i.get();
	}

	private static int test6() {
		long nanos = System.nanoTime();
		AtomicLong i = new AtomicLong();
		for (int k = 0; k < COUNT; k++)
			i.incrementAndGet();
		nanos = System.nanoTime() - nanos;
		System.out.format(MFT, "++atomic long", nanos);
		return (int)i.get();
	}
}
