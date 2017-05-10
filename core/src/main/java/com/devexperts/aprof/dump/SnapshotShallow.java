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

import java.io.*;
import java.util.Arrays;
import java.util.Comparator;

/**
 * @author Roman Elizarov
 */
public class SnapshotShallow implements Serializable {
	private static final long serialVersionUID = 0;

	public static final Comparator<SnapshotShallow> COMPARATOR_NAME = new Comparator<SnapshotShallow>() {
		public int compare(SnapshotShallow o1, SnapshotShallow o2) {
			return o1.name.compareTo(o2.name);
		}
	};

	public static final Comparator<SnapshotShallow> COMPARATOR_COUNT = new Comparator<SnapshotShallow>() {
		public int compare(SnapshotShallow o1, SnapshotShallow o2) {
			if (o2.getTotalCount() > o1.getTotalCount())
				return 1;
			if (o2.getTotalCount() < o1.getTotalCount())
				return -1;
			return o1.name.compareTo(o2.name);
		}
	};

	public static final Comparator<SnapshotShallow> COMPARATOR_SIZE = new Comparator<SnapshotShallow>() {
		public int compare(SnapshotShallow o1, SnapshotShallow o2) {
			if (o2.size > o1.size)
				return 1;
			if (o2.size < o1.size)
				return -1;
			return o1.name.compareTo(o2.name);
		}
	};

	protected static final long[] EMPTY_HISTO_COUNTS = new long[0];

	// --------- instance fields ---------

	private final String name;
	private long count;
	private long size;
	private final boolean isArray;
	private final long[] histoCounts;
	private transient long histoCountsSum; // recomputes on deserialization

	public SnapshotShallow() {
		name = null;
		isArray = false;
		histoCounts = EMPTY_HISTO_COUNTS;
	}

	public SnapshotShallow(String name, boolean isArray, int histogramLength) {
		this.name = name;
		this.isArray = isArray;
		this.histoCounts = histogramLength == 0 ? EMPTY_HISTO_COUNTS : new long[histogramLength];
	}

	public boolean hasChildren() {
		return false;
	}

	public String getName() {
		return name;
	}

	public long getCount() {
		return count;
	}

	public long getTotalCount() {
		return count + histoCountsSum;
	}

	public long getSize() {
		return size;
	}

	public boolean isArray() {
		return isArray;
	}

	public int getHistoCountsLength() {
		return histoCounts.length;
	}

	public long[] getHistoCounts() {
		return histoCounts;
	}


	public boolean isEmpty() {
		if (count != 0 || size != 0)
			return false;
		for (long count : histoCounts)
			if (count != 0)
				return false;
		return true;
	}

	public void clearShallow() {
		count = 0;
		size = 0;
		Arrays.fill(histoCounts, 0);
		histoCountsSum = 0;
	}

	public void ensurePositive() {
		count = Math.max(0, count);
		size = Math.max(0, size);
		histoCountsSum = 0;
		for (int i = 0; i < histoCounts.length; i++) {
			histoCounts[i] = Math.max(0, histoCounts[i]);
			histoCountsSum += histoCounts[i];
		}
	}

	public void add(long count, long size) {
		this.count += count;
		this.size += size;
	}

	public void sub(long count, long size) {
		this.count -= count;
		this.size -= size;
	}

	public void addHistoCount(int i, long histoCount) {
		histoCounts[i] += histoCount;
		histoCountsSum += histoCount;
	}

	public void subHistoCount(int i, long histoCount) {
		this.histoCounts[i] -= histoCount;
		this.histoCountsSum -= histoCount;
	}

	public void add(long count, long size, long[] histoCounts) {
		add(count, size);
		int n = Math.min(this.histoCounts.length, histoCounts.length);
		for (int i = 0; i < n; i++)
			addHistoCount(i, histoCounts[i]);
		for (int i = n; i < histoCounts.length; i++)
			count += histoCounts[i];
	}

	public void sub(long count, long size, long[] histoCounts) {
		sub(count, size);
		int n = Math.min(this.histoCounts.length, histoCounts.length);
		for (int i = 0; i < n; i++)
			subHistoCount(i, histoCounts[i]);
		for (int i = n; i < histoCounts.length; i++)
			count -= histoCounts[i];
	}

	public void addShallow(SnapshotShallow ss) {
		add(ss.count, ss.size, ss.histoCounts);
	}

	public void subShallow(SnapshotShallow ss) {
		sub(ss.count, ss.size, ss.histoCounts);
	}

	public boolean exceedsThreshold(SnapshotShallow total, double threshold) {
		return exceeds(threshold, getTotalCount(), total.getTotalCount()) || exceeds(threshold, getSize(), total.getSize());
	}

	private boolean exceeds(double threshold, long count, long total) {
		return count > total * threshold / 100;
	}

	/** Deserialization. */
	private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
		in.defaultReadObject();
		for (long cnt : histoCounts)
			histoCountsSum += cnt;
	}
}
