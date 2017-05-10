package com.devexperts.aprof.dump;

/*-
 * #%L
 * Aprof Core
 * %%
 * Copyright (C) 2002 - 2017 Devexperts, LLC
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */

import java.io.PrintWriter;
import java.util.Comparator;

import com.devexperts.aprof.AProfRegistry;
import com.devexperts.aprof.Configuration;
import com.devexperts.aprof.util.FastObjIntMap;

import static com.devexperts.aprof.util.FastFmtUtil.*;

/**
 * Formats collected dump snapshots.
 * <b>This class is not thread-safe</b>.
 *
 * @author Denis Davydov
 */
public class DumpFormatter {
	private static final int MAX_DEPTH = 5;

	private final Configuration config;

	private final SnapshotShallow possiblyEliminatedAllocations = new SnapshotShallow();
	private final SnapshotShallow[] rest = new SnapshotShallow[MAX_DEPTH];
	private final FastObjIntMap<String> classLevel = new FastObjIntMap<String>();
	private final FastObjIntMap<String> locationIndex = new FastObjIntMap<String>();
	private final SnapshotDeep locations;

	public DumpFormatter(Configuration config) {
		this.config = config;
		for (int i = 0; i < MAX_DEPTH; i++)
			rest[i] = new SnapshotShallow();
		locations = new SnapshotDeep(null, true, 0); // true to print avg size, 0 to skip printing histogram
	}

	public void dumpSnapshot(PrintWriter out, SnapshotRoot ss, String kind) {
		dumpSnapshotHeader(out, ss, kind);
		out.println("Top allocation-inducing locations with data types allocated from them");
		printlnTearLine(out, '-');
		dumpSnapshotByLocations(out, ss, SnapshotDeep.UNKNOWN);
		if (AProfRegistry.TRACK_TRANSFORM_DETAILS) {
			out.println("Top allocation-inducing locations inside transform with data types allocated from them");
			printlnTearLine(out, '-');
			dumpSnapshotByLocations(out, ss, AProfRegistry.TRANSFORM_NAME);
		}
		out.println("Top allocated data types with reverse location traces");
		printlnTearLine(out, '-');
		ss.sortChildrenDeep(getOutputComparator());
		dumpSnapshotByDataTypes(out, ss);
	}

	private Comparator<SnapshotShallow> getOutputComparator() {
		return config.isSize() ? SnapshotShallow.COMPARATOR_SIZE : SnapshotShallow.COMPARATOR_COUNT;
	}

	private void dumpSnapshotByLocations(PrintWriter out, SnapshotRoot ss, String insideOf) {
		// rebuild locations
		locations.clearDeep();
		locations.sortChildrenDeep(SnapshotShallow.COMPARATOR_NAME);
		locationIndex.fill(-1);
		for (int i = 0; i < ss.getUsed(); i++) {
			SnapshotDeep cs = ss.getChild(i);
			String dataTypeName = cs.getName();
			findLocationsDeep(cs, dataTypeName, cs.getHistoCountsLength(), insideOf);
		}
		locations.updateSnapshotSumDeep();
		// sort them and print
		locations.sortChildrenDeep(getOutputComparator());
		printLocationsDeep(out, 0, locations, ss);
	}

	private void findLocationsDeep(SnapshotDeep ss, String dataTypeName, int histoCountsLength, String insideOf) {
		if (!ss.hasChildren() && insideOf.equals(SnapshotDeep.UNKNOWN)) {
			processLeafLocation(ss, AProfRegistry.getLocationNameWithoutSuffix(ss.getName()), dataTypeName, histoCountsLength);
			return;
		}
		// has children -- go recursive with a special treatment for UNKNOWN children -- attribute them this location's name
		for (int i = 0; i < ss.getUsed(); i++) {
			SnapshotDeep cs = ss.getChild(i);
			if (cs.getName().equals(insideOf)) {
				assert !cs.hasChildren() : insideOf + " location shall not have children";
				processLeafLocation(cs, AProfRegistry.getLocationNameWithoutSuffix(ss.getName()), dataTypeName, histoCountsLength);
			} else
				findLocationsDeep(cs, dataTypeName, histoCountsLength, insideOf);
		}
	}

	private void processLeafLocation(SnapshotDeep ss, String name, String dataTypeName, int histoCountsLength) {
		// use hash index to find location (fast path)
		int i = locationIndex.get(name, -1);
		if (i < 0) {
			// if that does not work, then binary-search existing node (or create new one) and remember index in hash index
			i = locations.findOrCreateChildInSorted(name);
			locationIndex.put(name, i);
		}
		SnapshotDeep cs = locations.getChild(i);
		// append data type info for this location, true to always print average size
		SnapshotDeep child = cs.getOrCreateChild(dataTypeName, true, histoCountsLength);
		child.addShallow(ss);
		// count possibly eliminated allocations separately
		if (ss.isPossiblyEliminatedAllocation())
			child.getOrCreateChild("<possibly eliminated>").addShallow(ss);
	}

	public void dumpSnapshotHeader(PrintWriter out, SnapshotRoot ss, String kind) {
		out.println();
		//------ start with tear line
		printlnTearLine(out, '=');
		//------ Line #1
		out.print(kind + " allocation dump for ");
		printNum(out, ss.getTime());
		out.print(" ms (");
		printTimePeriod(out, ss.getTime());
		out.println(")");
		//------ Line #2
		out.print("Allocated ");
		if (config.isSize()) {
			printNum(out, ss.getSize());
			out.print(" bytes in ");
		}
		printNum(out, ss.getTotalCount());
		out.print(" objects of ");
		printNum(out, ss.countNonEmptyChildrenShallow());
		out.print(" classes in ");
		printNum(out, ss.countNonEmptyLeafs());
		out.println(" locations");
		//------ Line #3 (optional)
		if (config.isCheckEliminateAllocation()) {
			countPossibleEliminatedAllocations(ss);
			out.print("HotSpot had possibly eliminated allocation of ");
			if (config.isSize()) {
				printNumPercent(out, possiblyEliminatedAllocations.getSize(), ss.getSize());
				out.print(" bytes in ");
			}
			printNumPercent(out, possiblyEliminatedAllocations.getTotalCount(), ss.getTotalCount());
			out.println(" objects");
		}
		//------ end with tear line
		printlnTearLine(out, '=');
		out.println();
	}

	private void countPossibleEliminatedAllocations(SnapshotRoot ss) {
		possiblyEliminatedAllocations.clearShallow();
		countPossibleEliminatedAllocationsRec(ss);
	}

	private void countPossibleEliminatedAllocationsRec(SnapshotDeep ss) {
		if (ss.isPossiblyEliminatedAllocation()) {
			possiblyEliminatedAllocations.addShallow(ss);
			return;
		}
		for (int i = 0; i < ss.getUsed(); i++)
			countPossibleEliminatedAllocationsRec(ss.getChild(i));
	}

	public void dumpSnapshotByDataTypes(PrintWriter out, SnapshotRoot ss) {
		// compute class levels -- classes of level 0 are classes that exceed threshold
		classLevel.fill(Integer.MAX_VALUE);
		for (int csi = 0; csi < ss.getUsed(); csi++) {
			SnapshotDeep cs = ss.getChild(csi);
			classLevel.put(cs.getName(), cs.exceedsThreshold(ss, config.getThreshold()) ? 0 : Integer.MAX_VALUE);
		}
		// compute progressive higher levels
		for (int level = 0; level < config.getLevel(); level++)
			for (int csi = 0; csi < ss.getUsed(); csi++) {
				SnapshotDeep cs = ss.getChild(csi);
				if (classLevel.get(cs.getName()) == level)
					markClassLevelRec(cs, level);
			}

		// dump classes
		int cskipped = 0;
		rest[0].clearShallow();
		for (int csi = 0; csi < ss.getUsed(); csi++) {
			SnapshotDeep cs = ss.getChild(csi);
			if (!cs.isEmpty() && classLevel.get(cs.getName()) <= config.getLevel()) {
				out.print(cs.getName());
				printlnDetailsShallow(out, cs, ss, true, cs.isPossiblyEliminatedAllocation());
				printLocationsDeep(out, 1, cs, ss);
				out.println();
			} else if (!cs.isEmpty()) {
				cskipped++;
				rest[0].addShallow(cs);
			}
		}
		if (cskipped > 0) {
			out.print("... ");
			printNum(out, cskipped);
			out.print(" more below threshold");
			printlnDetailsShallow(out, rest[0], ss, true, false);
		}
	}

	private void printlnDetailsShallow(PrintWriter out, SnapshotShallow item, SnapshotShallow total, boolean printAvg,
		boolean possiblyEliminated)
	{
		out.print(": ");
		if (config.isSize()) {
			printNumPercent(out, item.getSize(), total.getSize());
			out.print(" bytes in ");
		}
		printNumPercent(out, item.getTotalCount(), total.getTotalCount());
		out.print(" objects");
		if (printAvg) {
			out.print(" ");
			printAvg(out, item.getSize(), item.getTotalCount());
		}
		long[] counts = item.getHistoCounts();
		if (counts.length > 0 && item.getTotalCount() > 0) {
			int lastNonZero = counts.length - 1;
			while (lastNonZero > 0 && counts[lastNonZero] == 0)
				lastNonZero--;
			if (counts[lastNonZero] != 0) {
				out.print(" [histogram: ");
				printNum(out, item.getCount()); // smallest bracket first
				for (int i = 0; i <= lastNonZero; i++) {
					out.print(" ");
					printNum(out, counts[i]);
				}
				out.print("]");
			}
		}
		if (possiblyEliminated)
			out.print("; possibly eliminated");
		out.println();
	}

	private static void printIndent(PrintWriter out, int depth) {
		for (int j = 0; j < depth; j++)
			out.print("\t");
	}

	private void markClassLevelRec(SnapshotDeep ss, int level) {
		for (int i = 0; i < ss.getUsed(); i++) {
			SnapshotDeep item = ss.getChild(i);
			String className = item.getName();
			if (item.exceedsThreshold(ss, config.getThreshold()) && className != null) {
				int oldLevel = classLevel.get(className);
				if (oldLevel > level + 1)
					classLevel.put(className, level + 1);
			}
			if (item.hasChildren())
				markClassLevelRec(item, level);
		}
	}

	private void printLocationsDeep(PrintWriter out, int depth, SnapshotDeep ss, SnapshotShallow total) {
		// count how many below threshold (1st pass)
		int shown = 0;
		int skipped = 0;
		for (int i = 0; i < ss.getUsed(); i++) {
			SnapshotDeep item = ss.getChild(i);
			if (item.isEmpty())
				continue; // ignore empty items
			// always show 1st item and all that exceed threshold
			if (shown == 0 || item.exceedsThreshold(total, config.getThreshold()))
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
			if (shown == 0 || printAll || item.exceedsThreshold(total, config.getThreshold())) {
				shown++;
				printIndent(out, depth);
				out.print(item.getName());
				printlnDetailsShallow(out, item, total, item.isArray(), item.isPossiblyEliminatedAllocation());
				if (item.hasChildren())
					printLocationsDeep(out, depth + 1, item, total);
				if (depth == 0)
					out.println(); // empty lines on top level
			} else {
				skipped++;
				rest[depth].addShallow(item);
			}
		}
		if (skipped > 0) {
			printIndent(out, depth);
			out.print("... ");
			printNum(out, skipped);
			out.print(" more below threshold");
			printlnDetailsShallow(out, rest[depth], total, ss.isArray(), false);
			if (depth == 0)
				out.println(); // empty lines on top level
		}
	}

}
