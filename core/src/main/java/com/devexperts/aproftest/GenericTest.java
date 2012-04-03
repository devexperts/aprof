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

import com.devexperts.aprof.Configuration;

/**
 * @author Dmitry Paraschenko
 */
class GenericTest implements TestCase {
	public String name() {
		return "generic";
	}

	public String verifyConfiguration(Configuration configuration) {
		if (!configuration.isLocationTracked(TRACK + ".*")) {
			return "track=" + TRACK;
		}
		return null;
	}

	public String[] getCheckedClasses() {
		return null;
	}

	public String getExpectedStatistics() {
		return STATISTICS;
	}

	public void doTest() {
		long time = System.currentTimeMillis();
		for (int i = 0; i < 100000; i++) {
			if (i % 10000 == 0)
				System.out.print('.');
			Tracked.a(1);
			Tracked.a(2);
			Tracked.a(3);
			b(1);
			b(2);
			b(3);
			new Entity1();
			new Entity2();
			new Entity3();
			new Entity4();
		}
		System.out.printf(" Test took %d ms\n", System.currentTimeMillis() - time);
	}

	private static void b(int i) {
		if (i <= 0) {
			new Tracked().dup();
			new Entity1();
			new Entity2();
			new Entity3();
			new Entity4().dup();
			Entity2[] arr = new Entity2[20];
			arr.clone();
		} else {
			Tracked.a(i - 1);
			b(i - 1);
		}
	}

	static class Tracked extends Entity1 implements Cloneable {
		public Tracked() {
			super(new Entity4());
		}

		private static void a(int i) {
			if (i <= 0) {
				new Tracked().dup();
				new Entity1();
				new Entity2();
				new Entity3();
				new Entity4().dup();
				Entity2[] arr = new Entity2[20];
				arr.clone();
			} else {
				a(i - 1);
				b(i - 1);
			}
		}

		private static Entity4 wrap(int value) {
			new Tracked().dup();
			new Entity1();
			new Entity2();
			return new Entity4().dup();
		}

		public Tracked dup() {
			try {
				return (Tracked)clone();
			} catch (CloneNotSupportedException e) {
				throw new InternalError();
			}
		}
	}

	private static class Entity1 {
		public Entity1() {
		}

		public Entity1(Entity4 e) {
		}
	}

	private static class Entity2 {
		private Entity2() {
			new Entity4().dup();
		}
	}

	private static class Entity3 extends Entity1 {
		private Entity3() {
			super(Tracked.wrap(1));
			new Entity4().dup();
		}
	}

	private static class Entity4 implements Cloneable {
		public Entity4 dup() {
			try {
				return (Entity4)clone();
			} catch (CloneNotSupportedException e) {
				throw new InternalError();
			}
		}
	}


	private static String TRACK = GenericTest.class.getCanonicalName() + "$" + Tracked.class.getSimpleName();

	private static String STATISTICS = "" +
			"Allocated 592,000,000 bytes in 40,400,000 objects in 60 locations of 6 classes\n" +
			"-------------------------------------------------------------------------------\n" +
			"com.devexperts.aproftest.GenericTest$Entity2[]: 268,800,000 (45%) bytes in 2,800,000 (6%) objects (avg size 96 bytes)\n" +
			"\tcom.devexperts.aproftest.GenericTest$Tracked.a: 134,400,000 (50%) bytes in 1,400,000 (50%) objects (avg size 96 bytes)\n" +
			"\t\tcom.devexperts.aproftest.GenericTest$Tracked.a: 134,400,000 (100%) bytes in 1,400,000 (100%) objects (avg size 96 bytes)\n" +
			"\t\t\tcom.devexperts.aproftest.GenericTest.b: 67,200,000 (50%) bytes in 700,000 (50%) objects (avg size 96 bytes)\n" +
			"\t\t\tcom.devexperts.aproftest.GenericTest.doTest: 67,200,000 (50%) bytes in 700,000 (50%) objects (avg size 96 bytes)\n" +
			"\tcom.devexperts.aproftest.GenericTest$Tracked.a*: 134,400,000 (50%) bytes in 1,400,000 (50%) objects (avg size 96 bytes)\n" +
			"\tcom.devexperts.aproftest.GenericTest.b: 134,400,000 (50%) bytes in 1,400,000 (50%) objects (avg size 96 bytes)\n" +
			"\t\tcom.devexperts.aproftest.GenericTest$Tracked.a: 105,600,000 (78%) bytes in 1,100,000 (78%) objects (avg size 96 bytes)\n" +
			"\t\t\tcom.devexperts.aproftest.GenericTest.doTest: 67,200,000 (63%) bytes in 700,000 (63%) objects (avg size 96 bytes)\n" +
			"\t\t\tcom.devexperts.aproftest.GenericTest.b: 38,400,000 (36%) bytes in 400,000 (36%) objects (avg size 96 bytes)\n" +
			"\t\t<unknown>: 28,800,000 (21%) bytes in 300,000 (21%) objects (avg size 96 bytes)\n" +
			"\tcom.devexperts.aproftest.GenericTest.b*: 134,400,000 (50%) bytes in 1,400,000 (50%) objects (avg size 96 bytes)\n" +
			"\n" +
			"com.devexperts.aproftest.GenericTest$Entity4: 161,600,000 (27%) bytes in 20,200,000 (50%) objects (avg size 8 bytes)\n" +
			"\tcom.devexperts.aproftest.GenericTest$Entity4.dup*: 115,200,000 (71%) bytes in 14,400,000 (71%) objects\n" +
			"\tcom.devexperts.aproftest.GenericTest$Entity2.<init>: 46,400,000 (28%) bytes in 5,800,000 (28%) objects\n" +
			"\t\tcom.devexperts.aproftest.GenericTest$Tracked.a: 40,000,000 (86%) bytes in 5,000,000 (86%) objects\n" +
			"\t\t\tcom.devexperts.aproftest.GenericTest.doTest: 22,400,000 (56%) bytes in 2,800,000 (56%) objects\n" +
			"\t\t\tcom.devexperts.aproftest.GenericTest.b: 17,600,000 (44%) bytes in 2,200,000 (44%) objects\n" +
			"\t\t<unknown>: 3,200,000 (6%) bytes in 400,000 (6%) objects\n" +
			"\t\tcom.devexperts.aproftest.GenericTest$Tracked.wrap: 3,200,000 (6%) bytes in 400,000 (6%) objects\n" +
			"\t\t\tcom.devexperts.aproftest.GenericTest$Entity3.<init>: 3,200,000 (100%) bytes in 400,000 (100%) objects\n" +
			"\tcom.devexperts.aproftest.GenericTest$Tracked.<init>: 45,600,000 (28%) bytes in 5,700,000 (28%) objects\n" +
			"\t\tcom.devexperts.aproftest.GenericTest$Tracked.a: 40,000,000 (87%) bytes in 5,000,000 (87%) objects\n" +
			"\t\t\tcom.devexperts.aproftest.GenericTest.doTest: 22,400,000 (56%) bytes in 2,800,000 (56%) objects\n" +
			"\t\t\tcom.devexperts.aproftest.GenericTest.b: 17,600,000 (44%) bytes in 2,200,000 (44%) objects\n" +
			"\t\tcom.devexperts.aproftest.GenericTest$Tracked.wrap: 3,200,000 (7%) bytes in 400,000 (7%) objects\n" +
			"\t\t\tcom.devexperts.aproftest.GenericTest$Entity3.<init>: 3,200,000 (100%) bytes in 400,000 (100%) objects\n" +
			"\t\tcom.devexperts.aproftest.GenericTest$Tracked.<init>: 2,400,000 (5%) bytes in 300,000 (5%) objects\n" +
			"\t\t\tcom.devexperts.aproftest.GenericTest.b: 2,400,000 (100%) bytes in 300,000 (100%) objects\n" +
			"\tcom.devexperts.aproftest.GenericTest$Entity3.<init>: 23,200,000 (14%) bytes in 2,900,000 (14%) objects\n" +
			"\t\tcom.devexperts.aproftest.GenericTest$Tracked.a: 20,000,000 (86%) bytes in 2,500,000 (86%) objects\n" +
			"\t\t\tcom.devexperts.aproftest.GenericTest.doTest: 11,200,000 (56%) bytes in 1,400,000 (56%) objects\n" +
			"\t\t\tcom.devexperts.aproftest.GenericTest.b: 8,800,000 (44%) bytes in 1,100,000 (44%) objects\n" +
			"\t\t<unknown>: 3,200,000 (13%) bytes in 400,000 (13%) objects\n" +
			"\tcom.devexperts.aproftest.GenericTest$Tracked.wrap: 23,200,000 (14%) bytes in 2,900,000 (14%) objects\n" +
			"\t\tcom.devexperts.aproftest.GenericTest$Tracked.a: 20,000,000 (86%) bytes in 2,500,000 (86%) objects\n" +
			"\t\t\tcom.devexperts.aproftest.GenericTest.doTest: 11,200,000 (56%) bytes in 1,400,000 (56%) objects\n" +
			"\t\t\tcom.devexperts.aproftest.GenericTest.b: 8,800,000 (44%) bytes in 1,100,000 (44%) objects\n" +
			"\t\tcom.devexperts.aproftest.GenericTest$Tracked.wrap: 3,200,000 (13%) bytes in 400,000 (13%) objects\n" +
			"\t\t\tcom.devexperts.aproftest.GenericTest$Entity3.<init>: 3,200,000 (100%) bytes in 400,000 (100%) objects\n" +
			"\tcom.devexperts.aproftest.GenericTest$Tracked.a: 11,200,000 (6%) bytes in 1,400,000 (6%) objects\n" +
			"\t\tcom.devexperts.aproftest.GenericTest$Tracked.a: 11,200,000 (100%) bytes in 1,400,000 (100%) objects\n" +
			"\t\t\tcom.devexperts.aproftest.GenericTest.b: 5,600,000 (50%) bytes in 700,000 (50%) objects\n" +
			"\t\t\tcom.devexperts.aproftest.GenericTest.doTest: 5,600,000 (50%) bytes in 700,000 (50%) objects\n" +
			"\tcom.devexperts.aproftest.GenericTest.b: 11,200,000 (6%) bytes in 1,400,000 (6%) objects\n" +
			"\t\tcom.devexperts.aproftest.GenericTest$Tracked.a: 8,800,000 (78%) bytes in 1,100,000 (78%) objects\n" +
			"\t\t\tcom.devexperts.aproftest.GenericTest.doTest: 5,600,000 (63%) bytes in 700,000 (63%) objects\n" +
			"\t\t\tcom.devexperts.aproftest.GenericTest.b: 3,200,000 (36%) bytes in 400,000 (36%) objects\n" +
			"\t\t<unknown>: 2,400,000 (21%) bytes in 300,000 (21%) objects\n" +
			"\tcom.devexperts.aproftest.GenericTest.doTest: 800,000 (0%) bytes in 100,000 (0%) objects\n" +
			"\n" +
			"com.devexperts.aproftest.GenericTest$Entity1: 46,400,000 (7%) bytes in 5,800,000 (14%) objects (avg size 8 bytes)\n" +
			"\tcom.devexperts.aproftest.GenericTest$Tracked.wrap: 23,200,000 (50%) bytes in 2,900,000 (50%) objects\n" +
			"\t\tcom.devexperts.aproftest.GenericTest$Tracked.a: 20,000,000 (86%) bytes in 2,500,000 (86%) objects\n" +
			"\t\t\tcom.devexperts.aproftest.GenericTest.doTest: 11,200,000 (56%) bytes in 1,400,000 (56%) objects\n" +
			"\t\t\tcom.devexperts.aproftest.GenericTest.b: 8,800,000 (44%) bytes in 1,100,000 (44%) objects\n" +
			"\t\tcom.devexperts.aproftest.GenericTest$Tracked.wrap: 3,200,000 (13%) bytes in 400,000 (13%) objects\n" +
			"\t\t\tcom.devexperts.aproftest.GenericTest$Entity3.<init>: 3,200,000 (100%) bytes in 400,000 (100%) objects\n" +
			"\tcom.devexperts.aproftest.GenericTest$Tracked.a: 11,200,000 (24%) bytes in 1,400,000 (24%) objects\n" +
			"\t\tcom.devexperts.aproftest.GenericTest$Tracked.a: 11,200,000 (100%) bytes in 1,400,000 (100%) objects\n" +
			"\t\t\tcom.devexperts.aproftest.GenericTest.b: 5,600,000 (50%) bytes in 700,000 (50%) objects\n" +
			"\t\t\tcom.devexperts.aproftest.GenericTest.doTest: 5,600,000 (50%) bytes in 700,000 (50%) objects\n" +
			"\tcom.devexperts.aproftest.GenericTest.b: 11,200,000 (24%) bytes in 1,400,000 (24%) objects\n" +
			"\t\tcom.devexperts.aproftest.GenericTest$Tracked.a: 8,800,000 (78%) bytes in 1,100,000 (78%) objects\n" +
			"\t\t\tcom.devexperts.aproftest.GenericTest.doTest: 5,600,000 (63%) bytes in 700,000 (63%) objects\n" +
			"\t\t\tcom.devexperts.aproftest.GenericTest.b: 3,200,000 (36%) bytes in 400,000 (36%) objects\n" +
			"\t\t<unknown>: 2,400,000 (21%) bytes in 300,000 (21%) objects\n" +
			"\tcom.devexperts.aproftest.GenericTest.doTest: 800,000 (1%) bytes in 100,000 (1%) objects\n" +
			"\n" +
			"com.devexperts.aproftest.GenericTest$Entity2: 46,400,000 (7%) bytes in 5,800,000 (14%) objects (avg size 8 bytes)\n" +
			"\tcom.devexperts.aproftest.GenericTest$Tracked.wrap: 23,200,000 (50%) bytes in 2,900,000 (50%) objects\n" +
			"\t\tcom.devexperts.aproftest.GenericTest$Tracked.a: 20,000,000 (86%) bytes in 2,500,000 (86%) objects\n" +
			"\t\t\tcom.devexperts.aproftest.GenericTest.doTest: 11,200,000 (56%) bytes in 1,400,000 (56%) objects\n" +
			"\t\t\tcom.devexperts.aproftest.GenericTest.b: 8,800,000 (44%) bytes in 1,100,000 (44%) objects\n" +
			"\t\tcom.devexperts.aproftest.GenericTest$Tracked.wrap: 3,200,000 (13%) bytes in 400,000 (13%) objects\n" +
			"\t\t\tcom.devexperts.aproftest.GenericTest$Entity3.<init>: 3,200,000 (100%) bytes in 400,000 (100%) objects\n" +
			"\tcom.devexperts.aproftest.GenericTest$Tracked.a: 11,200,000 (24%) bytes in 1,400,000 (24%) objects\n" +
			"\t\tcom.devexperts.aproftest.GenericTest$Tracked.a: 11,200,000 (100%) bytes in 1,400,000 (100%) objects\n" +
			"\t\t\tcom.devexperts.aproftest.GenericTest.b: 5,600,000 (50%) bytes in 700,000 (50%) objects\n" +
			"\t\t\tcom.devexperts.aproftest.GenericTest.doTest: 5,600,000 (50%) bytes in 700,000 (50%) objects\n" +
			"\tcom.devexperts.aproftest.GenericTest.b: 11,200,000 (24%) bytes in 1,400,000 (24%) objects\n" +
			"\t\tcom.devexperts.aproftest.GenericTest$Tracked.a: 8,800,000 (78%) bytes in 1,100,000 (78%) objects\n" +
			"\t\t\tcom.devexperts.aproftest.GenericTest.doTest: 5,600,000 (63%) bytes in 700,000 (63%) objects\n" +
			"\t\t\tcom.devexperts.aproftest.GenericTest.b: 3,200,000 (36%) bytes in 400,000 (36%) objects\n" +
			"\t\t<unknown>: 2,400,000 (21%) bytes in 300,000 (21%) objects\n" +
			"\tcom.devexperts.aproftest.GenericTest.doTest: 800,000 (1%) bytes in 100,000 (1%) objects\n" +
			"\n" +
			"com.devexperts.aproftest.GenericTest$Tracked: 45,600,000 (7%) bytes in 5,700,000 (14%) objects (avg size 8 bytes)\n" +
			"\tcom.devexperts.aproftest.GenericTest$Tracked.wrap: 23,200,000 (50%) bytes in 2,900,000 (50%) objects\n" +
			"\t\tcom.devexperts.aproftest.GenericTest$Tracked.a: 20,000,000 (86%) bytes in 2,500,000 (86%) objects\n" +
			"\t\t\tcom.devexperts.aproftest.GenericTest.doTest: 11,200,000 (56%) bytes in 1,400,000 (56%) objects\n" +
			"\t\t\tcom.devexperts.aproftest.GenericTest.b: 8,800,000 (44%) bytes in 1,100,000 (44%) objects\n" +
			"\t\tcom.devexperts.aproftest.GenericTest$Tracked.wrap: 3,200,000 (13%) bytes in 400,000 (13%) objects\n" +
			"\t\t\tcom.devexperts.aproftest.GenericTest$Entity3.<init>: 3,200,000 (100%) bytes in 400,000 (100%) objects\n" +
			"\tcom.devexperts.aproftest.GenericTest$Tracked.a: 11,200,000 (24%) bytes in 1,400,000 (24%) objects\n" +
			"\t\tcom.devexperts.aproftest.GenericTest$Tracked.a: 11,200,000 (100%) bytes in 1,400,000 (100%) objects\n" +
			"\t\t\tcom.devexperts.aproftest.GenericTest.b: 5,600,000 (50%) bytes in 700,000 (50%) objects\n" +
			"\t\t\tcom.devexperts.aproftest.GenericTest.doTest: 5,600,000 (50%) bytes in 700,000 (50%) objects\n" +
			"\tcom.devexperts.aproftest.GenericTest.b: 11,200,000 (24%) bytes in 1,400,000 (24%) objects\n" +
			"\t\tcom.devexperts.aproftest.GenericTest$Tracked.a: 8,800,000 (78%) bytes in 1,100,000 (78%) objects\n" +
			"\t\t\tcom.devexperts.aproftest.GenericTest.doTest: 5,600,000 (63%) bytes in 700,000 (63%) objects\n" +
			"\t\t\tcom.devexperts.aproftest.GenericTest.b: 3,200,000 (36%) bytes in 400,000 (36%) objects\n" +
			"\t\t<unknown>: 2,400,000 (21%) bytes in 300,000 (21%) objects\n" +
			"\n" +
			"com.devexperts.aproftest.GenericTest$Entity3: 23,200,000 (3%) bytes in 2,900,000 (7%) objects (avg size 8 bytes)\n" +
			"\tcom.devexperts.aproftest.GenericTest$Tracked.a: 11,200,000 (48%) bytes in 1,400,000 (48%) objects\n" +
			"\t\tcom.devexperts.aproftest.GenericTest$Tracked.a: 11,200,000 (100%) bytes in 1,400,000 (100%) objects\n" +
			"\t\t\tcom.devexperts.aproftest.GenericTest.b: 5,600,000 (50%) bytes in 700,000 (50%) objects\n" +
			"\t\t\tcom.devexperts.aproftest.GenericTest.doTest: 5,600,000 (50%) bytes in 700,000 (50%) objects\n" +
			"\tcom.devexperts.aproftest.GenericTest.b: 11,200,000 (48%) bytes in 1,400,000 (48%) objects\n" +
			"\t\tcom.devexperts.aproftest.GenericTest$Tracked.a: 8,800,000 (78%) bytes in 1,100,000 (78%) objects\n" +
			"\t\t\tcom.devexperts.aproftest.GenericTest.doTest: 5,600,000 (63%) bytes in 700,000 (63%) objects\n" +
			"\t\t\tcom.devexperts.aproftest.GenericTest.b: 3,200,000 (36%) bytes in 400,000 (36%) objects\n" +
			"\t\t<unknown>: 2,400,000 (21%) bytes in 300,000 (21%) objects\n" +
			"\tcom.devexperts.aproftest.GenericTest.doTest: 800,000 (3%) bytes in 100,000 (3%) objects\n" +
			"";
}
