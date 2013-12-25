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

package com.devexperts.sample;

/**
 * @author Dmitry Paraschenko
 */
public class FibonacciNumbers {
	private static int fib(int n) {
		if (n < 2)
			return 1;
		return fib(n - 1) + fib(n - 2);
	}

	public static void main(String[] args) {
		long time = System.currentTimeMillis();
		int n = Integer.parseInt(args[0]);
		System.out.printf("fib(%d)=%d\n", n, fib(n));
		System.out.printf("elapsed time=%d\n", System.currentTimeMillis() - time);
	}
}
