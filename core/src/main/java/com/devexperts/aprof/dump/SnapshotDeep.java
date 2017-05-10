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
import java.util.Comparator;

import com.devexperts.aprof.util.QuickSort;

/**
 * @author Dmitry Paraschenko
 */
public class SnapshotDeep extends SnapshotShallow {
	private static final long serialVersionUID = 0;

	/**
	 * Name of a special child node to keep counters from unknown children.
	 */
	public static final String UNKNOWN = "<unknown>";

	private static final SnapshotDeep[] EMPTY_CHILDREN = new SnapshotDeep[0];

	// --------- instance fields ---------

	private int used;
	private transient SnapshotDeep[] children = EMPTY_CHILDREN; // serialize only used
	private transient int sortedByNameTo;
	private boolean possiblyEliminatedAllocation; // viral flag -- inherited by all children and never cleared

	public SnapshotDeep() {}

	public SnapshotDeep(String name, boolean isArray, int histogramLength) {
		super(name, isArray, histogramLength);
	}

	@Override
	public boolean hasChildren() {
		return used > 0;
	}

	public int getUsed() {
		return used;
	}

	public SnapshotDeep getChild(int idx) {
		return children[idx];
	}

	public SnapshotDeep[] getChildren() {
		return children;
	}

	public boolean isPossiblyEliminatedAllocation() {
		return possiblyEliminatedAllocation;
	}

	public void setPossiblyEliminatedAllocation() {
		if (possiblyEliminatedAllocation)
			return;
		possiblyEliminatedAllocation = true;
		for (int i = 0; i < used; i++)
			children[i].setPossiblyEliminatedAllocation();
	}

	public void clearDeep() {
		clearShallow();
		for (int i = 0; i < used; i++)
			children[i].clearDeep();
	}

	// recompute sum for snapshot from children
	public void updateSnapshotSumShallow() {
		if (!hasChildren())
			return;
		clearShallow();
		for (int i = 0; i < used; i++)
			addShallow(getChild(i));
	}

	public void updateSnapshotSumDeep() {
		if (!hasChildren())
			return;
		for (int i = 0; i < used; i++)
			getChild(i).updateSnapshotSumDeep();
		updateSnapshotSumShallow();
	}

	public void addDeep(SnapshotDeep ss) {
		// copy possibly eliminated flag
		if (ss.isPossiblyEliminatedAllocation())
			setPossiblyEliminatedAllocation(); // also recursively mark all children
		// if this snapshot has children, but incoming snapshot has no children, then add incoming into UNKNOWN
		if (hasChildren() && !ss.hasChildren()) {
			addToUnknown(ss);
			return;
		}
		// if this snapshot has no children, but incoming snapshot has children, then move snapshot's total into UNKNOWN
		if (!hasChildren() && ss.hasChildren())
			addToUnknown(this);
		// now two snapshots have the same "childness" -- add their totals and children recursively
		addShallow(ss);
		sortChildrenShallow(COMPARATOR_NAME);
		ss.sortChildrenShallow(COMPARATOR_NAME);
		ensureChildrenCapacity(ss.used);
		int idx = 0;
		for (int i = 0; i < ss.used; i++) {
			SnapshotDeep other = ss.children[i];
			String name = other.getName();
			SnapshotDeep item = getOrCreateChildAt(idx =
				findChildInSortedFrom(idx, name), name, other.isArray(), other.getHistoCountsLength());
			item.addDeep(other);
		}
	}

	public void subDeep(SnapshotDeep ss) {
		// if this snapshot has children, but incoming snapshot has no children, then sub incoming from UNKNOWN
		if (hasChildren() && !ss.hasChildren()) {
			subFromUnknown(ss);
			return;
		}
		// if this snapshot has no children, but incoming snapshot has children, then move snapshot's total into UNKNOWN
		if (!hasChildren() && ss.hasChildren())
			addToUnknown(this);
		subShallow(ss);
		sortChildrenShallow(COMPARATOR_NAME);
		ss.sortChildrenShallow(COMPARATOR_NAME);
		ensureChildrenCapacity(ss.used);
		int idx = 0;
		for (int i = 0; i < ss.used; i++) {
			SnapshotDeep other = ss.children[i];
			String name = other.getName();
			SnapshotDeep item = getOrCreateChildAt(idx =
				findChildInSortedFrom(idx, name), name, other.isArray(), other.getHistoCountsLength());
			item.subDeep(other);
		}
	}

	public void addToUnknown(SnapshotShallow unknown) {
		if (unknown.isEmpty())
			return;
		getOrCreateChild(UNKNOWN).addShallow(unknown);
	}

	public void subFromUnknown(SnapshotShallow unknown) {
		if (unknown.isEmpty())
			return;
		getOrCreateChild(UNKNOWN).subShallow(unknown);
	}

	private void sortChildrenShallow(Comparator<SnapshotShallow> comparator) {
		QuickSort.sort(children, 0, used, comparator);
		if (comparator == COMPARATOR_NAME)
			sortedByNameTo = used;
		else
			sortedByNameTo = -1;
	}

	public void sortChildrenDeep(Comparator<SnapshotShallow> comparator) {
		sortChildrenShallow(comparator);
		for (int i = 0; i < used; i++)
			children[i].sortChildrenDeep(comparator);
	}

	public void ensureChildrenCapacity(int size) {
		int n = children.length;
		if (n < size) {
			int nn = Math.max(size, n + n / 4);
			SnapshotDeep[] newItems = new SnapshotDeep[nn];
			System.arraycopy(children, 0, newItems, 0, used);
			children = newItems;
		}
	}

	// PRE-CONDITION: sortChildrenShallow(COMPARATOR_NAME)
	public int findChildInSortedFrom(int i, String name) {
		assert sortedByNameTo >= 0;
		while (i < sortedByNameTo && name.compareTo(children[i].getName()) > 0)
			i++;
		return i;
	}

	// PRE-CONDITION: sortChildrenShallow(COMPARATOR_NAME)
	public int findChildInSorted(String name) {
		assert sortedByNameTo >= 0;
		int a = 0;
		int b = sortedByNameTo - 1;
		while (a < b) {
			int m = (a + b) / 2;
			int cmp = name.compareTo(children[m].getName());
			if (cmp < 0)
				b = m - 1;
			else if (cmp > 0)
				a = m + 1;
			else
				return m;
		}
		return a;
	}

	// PRE-CONDITION: sortChildrenShallow(COMPARATOR_NAME)
	public int findOrCreateChildInSorted(String name) {
		// inherit isArray and histogramLength attributes
		return findOrCreateChildAt(findChildInSorted(name), name, isArray(), getHistoCountsLength());
	}

	public int findChild(String name) {
		int i = 0;
		while (i < used && !name.equals(children[i].getName()))
			i++;
		return i;
	}

	public int findOrCreateChildAt(int index, String name, boolean isArray, int histogramLength) {
		if (index < used && name.equals(children[index].getName()))
			return index;
		ensureChildrenCapacity(used + 1);
		int i = used++;
		children[i] = new SnapshotDeep(name, isArray, histogramLength);
		if (isPossiblyEliminatedAllocation())
			children[i].setPossiblyEliminatedAllocation();
		return i;
	}

	public SnapshotDeep getOrCreateChildAt(int index, String name) {
		// inherit isArray and histogramLength attributes
		return getOrCreateChildAt(index, name, isArray(), getHistoCountsLength());
	}

	public SnapshotDeep getOrCreateChildAt(int index, String name, boolean isArray, int histogramLength) {
		int i = findOrCreateChildAt(index, name, isArray, histogramLength);
		return children[i];
	}

	public SnapshotDeep getOrCreateChild(String name) {
		return getOrCreateChildAt(findChild(name), name);
	}

	public SnapshotDeep getOrCreateChild(String name, boolean isArray, int histoCountsLength) {
		return getOrCreateChildAt(findChild(name), name, isArray, histoCountsLength);
	}

	public int countNonEmptyChildrenShallow() {
		int count = 0;
		for (int i = 0; i < used; i++) {
			SnapshotDeep item = children[i];
			if (!item.isEmpty())
				count++;
		}
		return count;
	}

	public int countNonEmptyLeafs() {
		int count = 0;
		for (int i = 0; i < used; i++) {
			SnapshotDeep item = children[i];
			if (!item.isEmpty())
				count += item.countNonEmptyLeafs();
		}
		return count == 0 ? 1 : count;
	}

	/** Serialization. */
	private void writeObject(ObjectOutputStream out) throws IOException {
		out.defaultWriteObject();
		for (int i = 0; i < used; i++)
			out.writeObject(children[i]);
	}

	/** Deserialization. */
	private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
		in.defaultReadObject();
		children = used == 0 ? EMPTY_CHILDREN : new SnapshotDeep[used];
		for (int i = 0; i < used; i++)
			children[i] = (SnapshotDeep)in.readObject();
	}
}
