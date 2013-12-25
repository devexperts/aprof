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

import com.devexperts.aprof.util.QuickSort;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Comparator;

/**
 * @author Dmitry Paraschenko
 */
public class Snapshot implements Serializable {
	private static final Snapshot[] EMPTY = new Snapshot[0];

	private final String id;
	private final long[] histoCounts;

	private long count;
	private long size;
	private volatile long histoCountsSum;

	private Snapshot[] items = EMPTY;
	private int used;

	public Snapshot() {
		id = null;
		histoCounts = new long[0];
	}

	public Snapshot(String id, int histoLength) {
		this.id = id;
		this.histoCounts = new long[histoLength];
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

	public long[] getCounts() {
		return histoCounts;
	}

	public boolean isEmpty() {
		if (count != 0 || size != 0) {
			return false;
		}
		for (long count : histoCounts) {
			if (count != 0) {
				return false;
			}
		}
		return true;
	}

	public String getId() {
		return id;
	}

	public int getUsed() {
		return used;
	}

	public boolean isList() {
		return used > 0;
	}

	public Snapshot getItem(int idx) {
		return items[idx];
	}

	public Snapshot[] getItems() {
		return items;
	}

	public void clear() {
		count = 0;
		size = 0;
		Arrays.fill(histoCounts, 0);
		histoCountsSum = 0;
	}

	public void positive() {
		count = Math.max(0, count);
		size = Math.max(0, size);
		histoCountsSum = 0;
		for (int i = 0; i < histoCounts.length; i++) {
			histoCounts[i] = Math.max(0, histoCounts[i]);
			histoCountsSum += histoCounts[i];
		}
	}

	public void clearDeep() {
		clear();
		for (int i = 0; i < used; i++) {
			items[i].clearDeep();
		}
	}

	public void add(long count, long size) {
		this.count += count;
		this.size += size;
	}

	public void add(long count, long size, long[] histoCounts) {
		this.count += count;
		this.size += size;
		for (int i = 0; i < this.histoCounts.length; i++) {
			this.histoCounts[i] += histoCounts[i];
			this.histoCountsSum += histoCounts[i];
		}
	}

	public void sub(long count, long size, long[] histoCounts) {
		this.count -= count;
		this.size -= size;
		for (int i = 0; i < this.histoCounts.length; i++) {
			this.histoCounts[i] -= histoCounts[i];
			this.histoCountsSum -= histoCounts[i];
		}
	}

	public void add(Snapshot ss) {
		add(ss.count, ss.size, ss.histoCounts);
	}

	public void sub(Snapshot ss) {
		sub(ss.count, ss.size, ss.histoCounts);
	}

	public void addAll(Snapshot ss) {
		add(ss);
		sortShallow(Snapshot.COMPARATOR_ID);
		ss.sortShallow(Snapshot.COMPARATOR_ID);
		ensureCapacity(ss.used);
		int idx = 0;
		for (int i = 0; i < ss.used; i++) {
			Snapshot other = ss.items[i];
			String id = other.id;
			Snapshot item = get(idx = move(idx, id), id, other.getCounts().length);
			item.addAll(other);
		}
	}

	public void sort(Comparator<Snapshot> comparator) {
		sortShallow(comparator);
		for (int i = 0; i < used; i++)
			items[i].sort(comparator);
	}

	private void sortShallow(Comparator<Snapshot> comparator) {
		QuickSort.sort(items, 0, used, comparator);
	}

	public void ensureCapacity(int size) {
		int n = items.length;
		if (n < size) {
			int nn = Math.max(size, n + n / 4);
			Snapshot[] newItems = new Snapshot[nn];
			System.arraycopy(items, 0, newItems, 0, used);
			items = newItems;
		}
	}

	public int move(int i, String id) {
		while (i < used && id.compareTo(items[i].id) > 0)
			i++;
		return i;
	}

	public Snapshot get(int i, String id) {
		return get(i, id, histoCounts.length);
	}

	public Snapshot get(int i, String id, int histoLength) {
		if (i < used && id.equals(items[i].id)) {
			return items[i];
		}
		ensureCapacity(used + 1);
		return items[used++] = new Snapshot(id, histoLength);
	}

	public int countNonEmptyShallow() {
		int count = 0;
		for (int i = 0; i < used; i++) {
			if (items[i].isEmpty()) {
				break;
			}
			count++;
		}
		return count;
	}

	public int countNonEmptyLeafs() {
		if (!isList()) {
			return 1;
		}
		int count = 0;
		for (int i = 0; i < used; i++) {
			Snapshot item = items[i];
			if (item.isEmpty()) {
				break;
			}
			count += item.countNonEmptyLeafs();
		}
		return count;
	}

	public static final Comparator<Snapshot> COMPARATOR_ID = new Comparator<Snapshot>() {
		public int compare(Snapshot o1, Snapshot o2) {
			return o1.id.compareTo(o2.id);
		}
	};

	public static final Comparator<Snapshot> COMPARATOR_COUNT = new Comparator<Snapshot>() {
		public int compare(Snapshot o1, Snapshot o2) {
			if (o2.getTotalCount() > o1.getTotalCount())
				return 1;
			if (o2.getTotalCount() < o1.getTotalCount())
				return -1;
			return o1.id.compareTo(o2.id);
		}
	};

	public static final Comparator<Snapshot> COMPARATOR_SIZE = new Comparator<Snapshot>() {
		public int compare(Snapshot o1, Snapshot o2) {
			if (o2.size > o1.size)
				return 1;
			if (o2.size < o1.size)
				return -1;
			return o1.id.compareTo(o2.id);
		}
	};

	public boolean exceedsThreshold(double threshold, Snapshot total) {
		return exceeds(threshold, getTotalCount(), total.getTotalCount()) || exceeds(threshold, getSize(), total.getSize());
	}

	private boolean exceeds(double threshold, long count, long total) {
		return count > total * threshold / 100;
	}

	/** Deserialization. */
	private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
		in.defaultReadObject();
		for (long cnt : histoCounts) {
			histoCountsSum += cnt;
		}
	}
}
