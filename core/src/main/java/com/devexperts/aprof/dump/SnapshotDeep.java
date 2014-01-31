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

import java.io.*;
import java.util.Comparator;

import com.devexperts.aprof.util.QuickSort;

/**
 * @author Dmitry Paraschenko
 */
public class SnapshotDeep extends SnapshotShallow {
	private static final long serialVersionUID = 0;

	private static final SnapshotDeep[] EMPTY_CHILDREN = new SnapshotDeep[0];

	// --------- instance fields ---------

	private int used;
	private transient SnapshotDeep[] children = EMPTY_CHILDREN; // serialize only used

	public SnapshotDeep() {}

	public SnapshotDeep(String name, int histoCountsLength) {
		super(name, histoCountsLength);
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

	public void clearDeep() {
		clearShallow();
		for (int i = 0; i < used; i++)
			children[i].clearDeep();
	}

	public void addDeep(SnapshotDeep ss) {
		addShallow(ss);
		sortChildrenShallow(COMPARATOR_NAME);
		ss.sortChildrenShallow(COMPARATOR_NAME);
		ensureChildrenCapacity(ss.used);
		int idx = 0;
		for (int i = 0; i < ss.used; i++) {
			SnapshotDeep other = ss.children[i];
			String id = other.name;
			SnapshotDeep item = getChild(idx = findChild(idx, id), id, other.getCounts().length);
			item.addDeep(other);
		}
	}

	private void sortChildrenShallow(Comparator<SnapshotShallow> comparator) {
		QuickSort.sort(children, 0, used, comparator);
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

	public int findChild(int fromIndex, String name) {
		while (fromIndex < used && name.compareTo(children[fromIndex].name) > 0)
			fromIndex++;
		return fromIndex;
	}

	public SnapshotDeep getChild(int index, String name) {
		return getChild(index, name, histoCounts.length);
	}

	public SnapshotDeep getChild(int index, String name, int histoCountsLength) {
		if (index < used && name.equals(children[index].name))
			return children[index];
		ensureChildrenCapacity(used + 1);
		return children[used++] = new SnapshotDeep(name, histoCountsLength);
	}

	public int countNonEmptyChildrenShallow() {
		int count = 0;
		for (int i = 0; i < used; i++) {
			if (children[i].isEmpty())
				break;
			count++;
		}
		return count;
	}

	public int countNonEmptyLeafs() {
		if (!hasChildren())
			return 1;
		int count = 0;
		for (int i = 0; i < used; i++) {
			SnapshotDeep item = children[i];
			if (item.isEmpty())
				break;
			count += item.countNonEmptyLeafs();
		}
		return count;
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
