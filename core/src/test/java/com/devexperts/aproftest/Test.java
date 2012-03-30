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

package com.devexperts.aproftest;

/**
 * @author Dmitry Paraschenko
 */
public class Test {
	public static void main(String[] args) {
		for (int i = 0; i < 10000; i++) {
			Tracked.a(1);
			Tracked.a(2);
			Tracked.a(3);
			b(1);
			b(2);
			b(3);
			new Entity();
		}
	}

	static class Tracked {
		private static void a(int i) {
			if (i <= 0) {
				new Entity();
			} else {
				a(i - 1);
				b(i - 1);
			}
		}
	}

	private static void b(int i) {
		if (i <= 0) {
			new Entity();
		} else {
			Tracked.a(i - 1);
			b(i - 1);
		}
	}

	static class Entity {
	}
}
