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

import com.devexperts.aprof.util.FastObjIntMap;

import java.io.PrintWriter;
import java.util.Comparator;

/**
 * @author Denis Davydov
 */
public class DumpFormatter {
	private static final int MAX_DEPTH = 5;

	private final Configuration config;

	private final Snapshot[] rest = new Snapshot[MAX_DEPTH];
	private final FastObjIntMap<String> classLevel = new FastObjIntMap<String>();

	public DumpFormatter(Configuration config) {
		this.config = config;
		for (int i = 0; i < MAX_DEPTH; i++)
			rest[i] = new Snapshot();
	}

	public void dumpSection(PrintWriter out, Snapshot ss, double threshold) {
		Comparator<Snapshot> comparator = config.isSize() ? Snapshot.COMPARATOR_SIZE : Snapshot.COMPARATOR_COUNT;
		ss.sort(comparator);
		printlnSummary(out, ss);
		out.println("-------------------------------------------------------------------------------");
		dumpSnapshot(out, ss, threshold);
	}

	private void printlnSummary(PrintWriter out, Snapshot ss) {
		out.print("Allocated ");
		if (config.isSize()) {
			printnum(out, ss.getSize());
			out.print(" bytes in ");
		}
		printnum(out, ss.getTotalCount());
		out.print(" objects in ");
		printnum(out, ss.countNonEmptyLeafs());
		out.print(" locations of ");
		printnum(out, ss.countNonEmptyShallow());
		out.println(" classes");
	}

	private void dumpSnapshot(PrintWriter out, Snapshot ss, double threshold) {
		// compute class levels -- classes of level 0 are classes that exceed threshold
		classLevel.fill(Integer.MAX_VALUE);
		for (int csi = 0; csi < ss.getUsed(); csi++) {
			Snapshot cs = ss.getItem(csi);
			classLevel.put(cs.getId(), cs.exceedsThreshold(threshold, ss) ? 0 : Integer.MAX_VALUE);
		}
		// compute progressive higher levels
		for (int level = 0; level < config.getLevel(); level++)
			for (int csi = 0; csi < ss.getUsed(); csi++) {
				Snapshot cs = ss.getItem(csi);
				if (classLevel.get(cs.getId()) == level)
					markClassLevelRec(cs, threshold, level);
			}

		// dump classes
		int cskipped = 0;
		rest[0].clear();
		for (int csi = 0; csi < ss.getUsed(); csi++) {
			Snapshot cs = ss.getItem(csi);
			if (!cs.isEmpty() && classLevel.get(cs.getId()) <= config.getLevel()) {
				boolean isArray = cs.getId().indexOf('[') >= 0;
				out.print(cs.getId());
				printlnDetails(out, cs, ss, true);
				printLocationsRec(out, 1, cs, threshold, isArray);
				out.println();
			} else if (!cs.isEmpty()) {
				cskipped++;
				rest[0].add(cs);
			}
		}
		if (cskipped > 0) {
			out.print("... ");
			printnum(out, cskipped);
			out.print(" more below threshold");
			printlnDetails(out, rest[0], ss, true);
		}
	}

	private void printlnDetails(PrintWriter out, Snapshot item, Snapshot total, boolean printAvg) {
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

	private void markClassLevelRec(Snapshot list, double threshold, int level) {
		for (int i = 0; i < list.getUsed(); i++) {
			Snapshot item = list.getItem(i);
			String className = item.getId();
			if (item.exceedsThreshold(threshold, list) && className != null) {
				int oldLevel = classLevel.get(className);
				if (oldLevel > level + 1)
					classLevel.put(className, level + 1);
			}
			if (item.isList())
				markClassLevelRec(item, threshold, level);
		}
	}

	private void printLocationsRec(PrintWriter out, int depth, Snapshot list, double threshold, boolean isArray) {
		// count how many below threshold (1st pass)
		int skipped = 0;
		for (int i = 0; i < list.getUsed(); i++) {
			Snapshot item = list.getItem(i);
			if (!item.exceedsThreshold(threshold, list) && !item.isEmpty())
				skipped++;
		}
		boolean printAll = skipped <= 2; // avoid ... 1 more and ... 2 more messages

		// print (2nd pass)
		skipped = 0;
		rest[depth].clear();
		for (int i = 0; i < list.getUsed(); i++) {
			Snapshot item = list.getItem(i);
			if ((printAll && !item.isEmpty()) || item.exceedsThreshold(threshold, list)) {
				printIndent(out, depth);
				out.print(item.getId());
				printlnDetails(out, item, list, isArray);
				if (item.isList())
					printLocationsRec(out, depth + 1, item, threshold, isArray);
			} else if (!item.isEmpty()) {
				skipped++;
				rest[depth].add(item);
			}
		}
		if (skipped > 0) {
			printIndent(out, depth);
			out.print("... ");
			printnum(out, skipped);
			out.print(" more below threshold");
			printlnDetails(out, rest[depth], list, isArray);
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
			printnum(out, count * 100 / total);
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
