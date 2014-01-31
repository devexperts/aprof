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

package com.devexperts.aprof.dump;

import java.io.PrintWriter;
import java.util.Comparator;

import com.devexperts.aprof.Configuration;
import com.devexperts.aprof.util.FastObjIntMap;

/**
 * @author Denis Davydov
 */
public class DumpFormatter {
	private static final int MAX_DEPTH = 5;

	private final Configuration config;

	private final SnapshotDeep[] rest = new SnapshotDeep[MAX_DEPTH];
	private final FastObjIntMap<String> classLevel = new FastObjIntMap<String>();

	public DumpFormatter(Configuration config) {
		this.config = config;
		for (int i = 0; i < MAX_DEPTH; i++)
			rest[i] = new SnapshotDeep();
	}

	public void dumpSection(PrintWriter out, SnapshotDeep ss, double threshold) {
		Comparator<SnapshotShallow> comparator = config.isSize() ? SnapshotShallow.COMPARATOR_SIZE : SnapshotShallow.COMPARATOR_COUNT;
		ss.sortChildrenDeep(comparator);
		printlnSummary(out, ss);
		out.println("-------------------------------------------------------------------------------");
		dumpSnapshot(out, ss, threshold);
	}

	private void printlnSummary(PrintWriter out, SnapshotDeep ss) {
		out.print("Allocated ");
		if (config.isSize()) {
			printnum(out, ss.getSize());
			out.print(" bytes in ");
		}
		printnum(out, ss.getTotalCount());
		out.print(" objects in ");
		printnum(out, ss.countNonEmptyLeafs());
		out.print(" locations of ");
		printnum(out, ss.countNonEmptyChildrenShallow());
		out.println(" classes");
	}

	private void dumpSnapshot(PrintWriter out, SnapshotDeep ss, double threshold) {
		// compute class levels -- classes of level 0 are classes that exceed threshold
		classLevel.fill(Integer.MAX_VALUE);
		for (int csi = 0; csi < ss.getUsed(); csi++) {
			SnapshotDeep cs = ss.getChild(csi);
			classLevel.put(cs.getName(), cs.exceedsThreshold(ss, threshold) ? 0 : Integer.MAX_VALUE);
		}
		// compute progressive higher levels
		for (int level = 0; level < config.getLevel(); level++)
			for (int csi = 0; csi < ss.getUsed(); csi++) {
				SnapshotDeep cs = ss.getChild(csi);
				if (classLevel.get(cs.getName()) == level)
					markClassLevelRec(cs, threshold, level);
			}

		// dump classes
		int cskipped = 0;
		rest[0].clearShallow();
		for (int csi = 0; csi < ss.getUsed(); csi++) {
			SnapshotDeep cs = ss.getChild(csi);
			if (!cs.isEmpty() && classLevel.get(cs.getName()) <= config.getLevel()) {
				boolean isArray = cs.getName().indexOf('[') >= 0;
				out.print(cs.getName());
				printlnDetailsShallow(out, cs, ss, true);
				printLocationsDeep(out, 1, cs, ss, threshold, isArray);
				out.println();
			} else if (!cs.isEmpty()) {
				cskipped++;
				rest[0].addShallow(cs);
			}
		}
		if (cskipped > 0) {
			out.print("... ");
			printnum(out, cskipped);
			out.print(" more below threshold");
			printlnDetailsShallow(out, rest[0], ss, true);
		}
	}

	private void printlnDetailsShallow(PrintWriter out, SnapshotShallow item, SnapshotShallow total, boolean printAvg) {
		out.print(": ");
		if (config.isSize()) {
			printp(out, item.getSize(), total.getSize());
			out.print(" bytes in ");
		}
		printp(out, item.getTotalCount(), total.getTotalCount());
		out.print(" objects");
		if (printAvg) {
			out.print(" ");
			printavg(out, item.getSize(), item.getTotalCount());
		}
		long[] counts = item.getCounts();
		if (counts != null && counts.length > 1) {
			out.print(" [histogram: ");
			int lastNonZero = counts.length - 1;
			while (lastNonZero > 0 && counts[lastNonZero] == 0) {
				lastNonZero--;
			}
			long count = item.getCount();
			if (count != 0) {
				out.print("(");
				out.print(count);
				out.print(") ");
			}
			for (int i = 0; i < lastNonZero; i++) {
				out.print(counts[i]);
				out.print(" ");
			}
			out.print(counts[lastNonZero]);
			out.print("]");
		}
		out.println();
	}

	private static void printIndent(PrintWriter out, int depth) {
		for (int j = 0; j < depth; j++)
			out.print("\t");
	}

	private void markClassLevelRec(SnapshotDeep ss, double threshold, int level) {
		for (int i = 0; i < ss.getUsed(); i++) {
			SnapshotDeep item = ss.getChild(i);
			String className = item.getName();
			if (item.exceedsThreshold(ss, threshold) && className != null) {
				int oldLevel = classLevel.get(className);
				if (oldLevel > level + 1)
					classLevel.put(className, level + 1);
			}
			if (item.hasChildren())
				markClassLevelRec(item, threshold, level);
		}
	}

	private void printLocationsDeep(PrintWriter out, int depth, SnapshotDeep ss, SnapshotShallow total,
		double threshold, boolean isArray)
	{
		// count how many below threshold (1st pass)
		int shown = 0;
		int skipped = 0;
		for (int i = 0; i < ss.getUsed(); i++) {
			SnapshotDeep item = ss.getChild(i);
			if (item.isEmpty())
				continue; // ignore empty items
			// always show 1st item and all that exceed threshold
			if (shown == 0 || item.exceedsThreshold(total, threshold))
				shown++;
			else
				skipped++;
		}
		boolean printAll = skipped <= 2; // avoid ... 1 more and ... 2 more messages

		// print (2nd pass)
		shown = 0;
		skipped = 0;
		rest[depth].clearShallow();
		for (int i = 0; i < ss.getUsed(); i++) {
			SnapshotDeep item = ss.getChild(i);
			if (item.isEmpty())
				continue; // ignore empty items
			if (shown == 0 || printAll || item.exceedsThreshold(total, threshold)) {
				shown++;
				printIndent(out, depth);
				out.print(item.getName());
				printlnDetailsShallow(out, item, total, isArray);
				if (item.hasChildren())
					printLocationsDeep(out, depth + 1, item, total, threshold, isArray);
			} else {
				skipped++;
				rest[depth].addShallow(item);
			}
		}
		if (skipped > 0) {
			printIndent(out, depth);
			out.print("... ");
			printnum(out, skipped);
			out.print(" more below threshold");
			printlnDetailsShallow(out, rest[depth], total, isArray);
		}
	}

	static void printnum(PrintWriter out, long value) {
		boolean fill = false;
		for (long x = 1000000000000000000L; x >= 1; x /= 1000) {
			if (value >= x || fill) {
				if (fill)
					out.print(",");
				print3(out, (int)(value / x), fill);
				value = value % x;
				fill = true;
			}
		}
		if (!fill)
			out.print("0");
	}

	private static void print3(PrintWriter out, int value, boolean fill) {
		if (fill || value >= 100)
			out.print((char)(value / 100 + '0'));
		print2(out, value, fill);
	}

	private static void print2(PrintWriter out, int value, boolean fill) {
		if (fill || value >= 10)
			out.print((char)(value / 10 % 10 + '0'));
		out.print((char)(value % 10 + '0'));
	}

	private static void printavg(PrintWriter out, long size, long count) {
		out.print("(avg size ");
		printnum(out, Math.round((double)size / count));
		out.print(" bytes)");
	}

	static void printp(PrintWriter out, long count, long total) {
		printnum(out, count);
		if (total > 0) {
			out.print(" (");
			long pp = count * 10000 / total;
			printnum(out, pp / 100);
			out.print(".");
			print2(out, (int)(pp % 100), true);
			out.print("%)");
		}
	}

	static void printtime(PrintWriter out, long millis) {
		long hour = millis / (60 * 60000);
		int min = (int)(millis / 60000 % 60);
		int sec = (int)(millis / 1000 % 60);
		printnum(out, hour);
		out.print("h");
		print2(out, min, true);
		out.print("m");
		print2(out, sec, true);
		out.print("s");
	}
}
