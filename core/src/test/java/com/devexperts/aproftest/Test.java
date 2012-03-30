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

import com.devexperts.aprof.AProfRegistry;
import com.devexperts.aprof.Snapshot;
import com.devexperts.aprof.Version;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;

/**
 * @author Dmitry Paraschenko
 */
public class Test {
	public static void main(String[] args) {
		System.out.println("Testing " + Version.get());

		// clearing STATISTICS
		AProfRegistry.makeSnapshot(new Snapshot());

		long time = System.currentTimeMillis();
		for (int i = 0; i < 100000; i++) {
			if (i % 10000 == 0)
				System.out.println(i);
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
		System.out.printf("Test took %d ms\n", System.currentTimeMillis() - time);

		// retrieving STATISTICS
		Snapshot snapshot = new Snapshot();
		AProfRegistry.makeSnapshot(snapshot);

		for (int i = 0; i < snapshot.getUsed(); i++) {
			Snapshot child = snapshot.getItem(i);
			if (!child.getId().startsWith("com.devexperts.aproftest.Test$Entity")) {
				child.clearDeep();
			}
		}

		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		PrintWriter out = new PrintWriter(bos);
		AProfRegistry.getDumpFormatter().dumpSection(out, snapshot, 0);
		out.flush();
		String result = new String(bos.toByteArray());
		result = result.substring(result.indexOf('\n')).trim();
		result = result.substring(result.indexOf('\n')).trim();

		String resultCopy = result;
		String statistics = STATISTICS.trim();
		for (int lid = 0; ; lid++) {
			if (result.isEmpty() || statistics.isEmpty()) {
				break;
			}
			int rpos = result.indexOf('\n');
			if (rpos < 0)
				rpos = result.length();
			String rline = result.substring(0, rpos).trim();
			result = result.substring(rpos).trim();
			int spos = statistics.indexOf('\n');
			if (spos < 0)
				spos = statistics.length();
			String sline = statistics.substring(0, spos).trim();
			statistics = statistics.substring(spos).trim();

			if (!rline.equals(sline)) {
				System.out.println(lid + ": received: ");
				System.out.println(rline);
				System.out.println(lid + ": expected: ");
				System.out.println(sline);
			}

			if (result.isEmpty() != statistics.isEmpty()) {
				System.out.println("end: received: ");
				System.out.println(rline);
				System.out.println("end: expected: ");
				System.out.println(sline);
			}
		}
		
		if (result.isEmpty() && statistics.isEmpty()) {
			System.out.println("Test passed");
		}
	}

	private static void b(int i) {
		if (i <= 0) {
			new Entity1();
			new Entity2();
			new Entity3();
			new Entity4();
		} else {
			Tracked.a(i - 1);
			b(i - 1);
		}
	}

	static class Tracked extends Entity1 {
		public Tracked() {
			super(new Entity4());
		}

		private static void a(int i) {
			if (i <= 0) {
				new Entity1();
				new Entity2();
				new Entity3();
				new Entity4();
			} else {
				a(i - 1);
				b(i - 1);
			}
		}
		
		private static Entity4 wrap(int value) {
			new Entity1();
			new Entity2();
			return new Entity4();
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
			new Entity4();
		}
	}

	private static class Entity3 extends Entity1 {
		private Entity3() {
			super(Tracked.wrap(1));
			new Entity4();
		}
	}

	private static class Entity4 {
	}
	
	
	private static String STATISTICS =
			"com.devexperts.aproftest.Test$Entity4: 116,000,000 (49%) bytes in 14,500,000 (49%) objects (avg size 8 bytes)\n" +
			"\tcom.devexperts.aproftest.Test$Entity2.<init>: 46,400,000 (40%) bytes in 5,800,000 (40%) objects\n" +
			"\t\tcom.devexperts.aproftest.Test$Tracked.a: 40,000,000 (86%) bytes in 5,000,000 (86%) objects\n" +
			"\t\t\tcom.devexperts.aproftest.Test.main: 22,400,000 (56%) bytes in 2,800,000 (56%) objects\n" +
			"\t\t\tcom.devexperts.aproftest.Test.b: 17,600,000 (44%) bytes in 2,200,000 (44%) objects\n" +
			"\t\t<unknown>: 3,200,000 (6%) bytes in 400,000 (6%) objects\n" +
			"\t\tcom.devexperts.aproftest.Test$Tracked.wrap: 3,200,000 (6%) bytes in 400,000 (6%) objects\n" +
			"\t\t\tcom.devexperts.aproftest.Test$Entity3.<init>: 3,200,000 (100%) bytes in 400,000 (100%) objects\n" +
			"\tcom.devexperts.aproftest.Test$Entity3.<init>: 23,200,000 (20%) bytes in 2,900,000 (20%) objects\n" +
			"\t\tcom.devexperts.aproftest.Test$Tracked.a: 20,000,000 (86%) bytes in 2,500,000 (86%) objects\n" +
			"\t\t\tcom.devexperts.aproftest.Test.main: 11,200,000 (56%) bytes in 1,400,000 (56%) objects\n" +
			"\t\t\tcom.devexperts.aproftest.Test.b: 8,800,000 (44%) bytes in 1,100,000 (44%) objects\n" +
			"\t\t<unknown>: 3,200,000 (13%) bytes in 400,000 (13%) objects\n" +
			"\tcom.devexperts.aproftest.Test$Tracked.wrap: 23,200,000 (20%) bytes in 2,900,000 (20%) objects\n" +
			"\t\tcom.devexperts.aproftest.Test$Tracked.a: 20,000,000 (86%) bytes in 2,500,000 (86%) objects\n" +
			"\t\t\tcom.devexperts.aproftest.Test.main: 11,200,000 (56%) bytes in 1,400,000 (56%) objects\n" +
			"\t\t\tcom.devexperts.aproftest.Test.b: 8,800,000 (44%) bytes in 1,100,000 (44%) objects\n" +
			"\t\tcom.devexperts.aproftest.Test$Tracked.wrap: 3,200,000 (13%) bytes in 400,000 (13%) objects\n" +
			"\t\t\tcom.devexperts.aproftest.Test$Entity3.<init>: 3,200,000 (100%) bytes in 400,000 (100%) objects\n" +
			"\tcom.devexperts.aproftest.Test$Tracked.a: 11,200,000 (9%) bytes in 1,400,000 (9%) objects\n" +
			"\t\tcom.devexperts.aproftest.Test$Tracked.a: 11,200,000 (100%) bytes in 1,400,000 (100%) objects\n" +
			"\t\t\tcom.devexperts.aproftest.Test.b: 5,600,000 (50%) bytes in 700,000 (50%) objects\n" +
			"\t\t\tcom.devexperts.aproftest.Test.main: 5,600,000 (50%) bytes in 700,000 (50%) objects\n" +
			"\tcom.devexperts.aproftest.Test.b: 11,200,000 (9%) bytes in 1,400,000 (9%) objects\n" +
			"\t\tcom.devexperts.aproftest.Test$Tracked.a: 8,800,000 (78%) bytes in 1,100,000 (78%) objects\n" +
			"\t\t\tcom.devexperts.aproftest.Test.main: 5,600,000 (63%) bytes in 700,000 (63%) objects\n" +
			"\t\t\tcom.devexperts.aproftest.Test.b: 3,200,000 (36%) bytes in 400,000 (36%) objects\n" +
			"\t\t<unknown>: 2,400,000 (21%) bytes in 300,000 (21%) objects\n" +
			"\tcom.devexperts.aproftest.Test.main: 800,000 (0%) bytes in 100,000 (0%) objects\n" +
			"\n" +
			"com.devexperts.aproftest.Test$Entity1: 46,400,000 (19%) bytes in 5,800,000 (19%) objects (avg size 8 bytes)\n" +
			"\tcom.devexperts.aproftest.Test$Tracked.wrap: 23,200,000 (50%) bytes in 2,900,000 (50%) objects\n" +
			"\t\tcom.devexperts.aproftest.Test$Tracked.a: 20,000,000 (86%) bytes in 2,500,000 (86%) objects\n" +
			"\t\t\tcom.devexperts.aproftest.Test.main: 11,200,000 (56%) bytes in 1,400,000 (56%) objects\n" +
			"\t\t\tcom.devexperts.aproftest.Test.b: 8,800,000 (44%) bytes in 1,100,000 (44%) objects\n" +
			"\t\tcom.devexperts.aproftest.Test$Tracked.wrap: 3,200,000 (13%) bytes in 400,000 (13%) objects\n" +
			"\t\t\tcom.devexperts.aproftest.Test$Entity3.<init>: 3,200,000 (100%) bytes in 400,000 (100%) objects\n" +
			"\tcom.devexperts.aproftest.Test$Tracked.a: 11,200,000 (24%) bytes in 1,400,000 (24%) objects\n" +
			"\t\tcom.devexperts.aproftest.Test$Tracked.a: 11,200,000 (100%) bytes in 1,400,000 (100%) objects\n" +
			"\t\t\tcom.devexperts.aproftest.Test.b: 5,600,000 (50%) bytes in 700,000 (50%) objects\n" +
			"\t\t\tcom.devexperts.aproftest.Test.main: 5,600,000 (50%) bytes in 700,000 (50%) objects\n" +
			"\tcom.devexperts.aproftest.Test.b: 11,200,000 (24%) bytes in 1,400,000 (24%) objects\n" +
			"\t\tcom.devexperts.aproftest.Test$Tracked.a: 8,800,000 (78%) bytes in 1,100,000 (78%) objects\n" +
			"\t\t\tcom.devexperts.aproftest.Test.main: 5,600,000 (63%) bytes in 700,000 (63%) objects\n" +
			"\t\t\tcom.devexperts.aproftest.Test.b: 3,200,000 (36%) bytes in 400,000 (36%) objects\n" +
			"\t\t<unknown>: 2,400,000 (21%) bytes in 300,000 (21%) objects\n" +
			"\tcom.devexperts.aproftest.Test.main: 800,000 (1%) bytes in 100,000 (1%) objects\n" +
			"\n" +
			"com.devexperts.aproftest.Test$Entity2: 46,400,000 (19%) bytes in 5,800,000 (19%) objects (avg size 8 bytes)\n" +
			"\tcom.devexperts.aproftest.Test$Tracked.wrap: 23,200,000 (50%) bytes in 2,900,000 (50%) objects\n" +
			"\t\tcom.devexperts.aproftest.Test$Tracked.a: 20,000,000 (86%) bytes in 2,500,000 (86%) objects\n" +
			"\t\t\tcom.devexperts.aproftest.Test.main: 11,200,000 (56%) bytes in 1,400,000 (56%) objects\n" +
			"\t\t\tcom.devexperts.aproftest.Test.b: 8,800,000 (44%) bytes in 1,100,000 (44%) objects\n" +
			"\t\tcom.devexperts.aproftest.Test$Tracked.wrap: 3,200,000 (13%) bytes in 400,000 (13%) objects\n" +
			"\t\t\tcom.devexperts.aproftest.Test$Entity3.<init>: 3,200,000 (100%) bytes in 400,000 (100%) objects\n" +
			"\tcom.devexperts.aproftest.Test$Tracked.a: 11,200,000 (24%) bytes in 1,400,000 (24%) objects\n" +
			"\t\tcom.devexperts.aproftest.Test$Tracked.a: 11,200,000 (100%) bytes in 1,400,000 (100%) objects\n" +
			"\t\t\tcom.devexperts.aproftest.Test.b: 5,600,000 (50%) bytes in 700,000 (50%) objects\n" +
			"\t\t\tcom.devexperts.aproftest.Test.main: 5,600,000 (50%) bytes in 700,000 (50%) objects\n" +
			"\tcom.devexperts.aproftest.Test.b: 11,200,000 (24%) bytes in 1,400,000 (24%) objects\n" +
			"\t\tcom.devexperts.aproftest.Test$Tracked.a: 8,800,000 (78%) bytes in 1,100,000 (78%) objects\n" +
			"\t\t\tcom.devexperts.aproftest.Test.main: 5,600,000 (63%) bytes in 700,000 (63%) objects\n" +
			"\t\t\tcom.devexperts.aproftest.Test.b: 3,200,000 (36%) bytes in 400,000 (36%) objects\n" +
			"\t\t<unknown>: 2,400,000 (21%) bytes in 300,000 (21%) objects\n" +
			"\tcom.devexperts.aproftest.Test.main: 800,000 (1%) bytes in 100,000 (1%) objects\n" +
			"\n" +
			"com.devexperts.aproftest.Test$Entity3: 23,200,000 (9%) bytes in 2,900,000 (9%) objects (avg size 8 bytes)\n" +
			"\tcom.devexperts.aproftest.Test$Tracked.a: 11,200,000 (48%) bytes in 1,400,000 (48%) objects\n" +
			"\t\tcom.devexperts.aproftest.Test$Tracked.a: 11,200,000 (100%) bytes in 1,400,000 (100%) objects\n" +
			"\t\t\tcom.devexperts.aproftest.Test.b: 5,600,000 (50%) bytes in 700,000 (50%) objects\n" +
			"\t\t\tcom.devexperts.aproftest.Test.main: 5,600,000 (50%) bytes in 700,000 (50%) objects\n" +
			"\tcom.devexperts.aproftest.Test.b: 11,200,000 (48%) bytes in 1,400,000 (48%) objects\n" +
			"\t\tcom.devexperts.aproftest.Test$Tracked.a: 8,800,000 (78%) bytes in 1,100,000 (78%) objects\n" +
			"\t\t\tcom.devexperts.aproftest.Test.main: 5,600,000 (63%) bytes in 700,000 (63%) objects\n" +
			"\t\t\tcom.devexperts.aproftest.Test.b: 3,200,000 (36%) bytes in 400,000 (36%) objects\n" +
			"\t\t<unknown>: 2,400,000 (21%) bytes in 300,000 (21%) objects\n" +
			"\tcom.devexperts.aproftest.Test.main: 800,000 (3%) bytes in 100,000 (3%) objects";
}
