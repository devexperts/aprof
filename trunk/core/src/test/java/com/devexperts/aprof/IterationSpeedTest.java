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

import java.io.IOException;

/**
 * @author Dmitry Paraschenko
 */
public class IterationSpeedTest {
	private static int invPoint;
	private static int invMethod;
	private static int location;

	public static void main(String[] args) throws IOException {
		AProfRegistry.init(new Configuration(), new ClassNameResolver() {
			public String resolve(String id) {
				return id;
			}
		});
		invPoint = AProfRegistry.registerLocation(IterationSpeedTest.class.getName() + ".testAprof");
		invMethod = AProfRegistry.registerLocation(Integer.class.getName() + ".valueOf");
		location = AProfRegistry.registerAllocationPoint(Integer.class.getName(), Integer.class.getName() + ".valueOf");

		testNonAprof(1000000);
		testAprof(1000000);

		int length = 100000000;

		long time = System.currentTimeMillis();
		testNonAprof(length);
		System.out.printf("nonaprof=%d\n", System.currentTimeMillis() - time);

		time = System.currentTimeMillis();
		testAprof(length);
		System.out.printf("aprof=%d\n", System.currentTimeMillis() - time);


		System.out.println();


		time = System.currentTimeMillis();
		for (int i = 0; i < length; i++)
			LocationStack.get();
		System.out.printf("stack=%d\n", System.currentTimeMillis() - time);

		LocationStack stack = LocationStack.get();

		time = System.currentTimeMillis();
		for (int i = 0; i < length; i++)
			stack.addInvocationPoint(invPoint);
		System.out.printf("markP-stack=%d\n", System.currentTimeMillis() - time);

		time = System.currentTimeMillis();
		for (int i = 0; i < length; i++)
			stack.removeInvocationPoint();
		System.out.printf("unmarkP-stack=%d\n", System.currentTimeMillis() - time);

		time = System.currentTimeMillis();
		for (int i = 0; i < length; i++)
			stack.addInvokedMethod(invMethod);
		System.out.printf("markM-stack=%d\n", System.currentTimeMillis() - time);

		time = System.currentTimeMillis();
		for (int i = 0; i < length; i++)
			stack.removeInvokedMethod();
		System.out.printf("unmarkM-stack=%d\n", System.currentTimeMillis() - time);

//		time = System.currentTimeMillis();
//		for (int i = 0; i < length; i++)
//			AProfOps.allocate(location);
//		System.out.printf("allocate=%d\n", System.currentTimeMillis() - time);

		System.out.println();

		time = System.currentTimeMillis();
		for (int i = 0; i < length; i++)
			LocationStack.get().addInvocationPoint(invPoint);
		System.out.printf("markP=%d\n", System.currentTimeMillis() - time);

		time = System.currentTimeMillis();
		for (int i = 0; i < length; i++)
			LocationStack.get().removeInvocationPoint();
		System.out.printf("unmarkP=%d\n", System.currentTimeMillis() - time);

		time = System.currentTimeMillis();
		for (int i = 0; i < length; i++)
			LocationStack.get().addInvokedMethod(invMethod);
		System.out.printf("markM=%d\n", System.currentTimeMillis() - time);

		time = System.currentTimeMillis();
		for (int i = 0; i < length; i++)
			LocationStack.get().removeInvokedMethod();
		System.out.printf("unmarkM=%d\n", System.currentTimeMillis() - time);

		time = System.currentTimeMillis();
		for (int i = 0; i < length; i++)
			AProfOps.allocate(location);
		System.out.printf("allocate=%d\n", System.currentTimeMillis() - time);
	}

	private static void testNonAprof(int length) {
		for (int i = 0; i < length; i++) {
			Integer.valueOf(i);
		}
	}

	private static void testAprof(int length) {
		for (int i = 0; i < length; i++) {
			LocationStack.get().addInvocationPoint(invPoint);
			try {
				LocationStack.get().addInvokedMethod(invMethod);
				Integer.valueOf(i);
				AProfOps.allocate(location);
				LocationStack.get().removeInvokedMethod();
			} finally {
				LocationStack.get().removeInvocationPoint();
			}
		}
	}
}
