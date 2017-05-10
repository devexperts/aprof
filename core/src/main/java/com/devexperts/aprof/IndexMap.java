package com.devexperts.aprof;

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

import com.devexperts.aprof.util.UnsafeHolder;

@Internal
class IndexMap<T extends IndexMap> {
	private static final int COUNT_OVERFLOW_THRESHOLD = 1 << 30;

	private static final long COUNT_OFFSET;
	private static final long SIZE_OFFSET;
	private static final int INT_ARRAY_BASE_OFFSET;
	private static final int INT_ARRAY_INDEX_SCALE;

	static {
		try {
			COUNT_OFFSET = UnsafeHolder.UNSAFE.objectFieldOffset(IndexMap.class.getDeclaredField("count"));
			SIZE_OFFSET = UnsafeHolder.UNSAFE.objectFieldOffset(IndexMap.class.getDeclaredField("size"));
			INT_ARRAY_BASE_OFFSET = UnsafeHolder.UNSAFE.arrayBaseOffset(int[].class);
			INT_ARRAY_INDEX_SCALE = UnsafeHolder.UNSAFE.arrayIndexScale(int[].class);
		} catch (Throwable t) {
			throw new ExceptionInInitializerError(t);
		}
	}

	/**
	 * Location id in AProfRegistry locations.
	 */
	private final int location;

	/**
	 * Class histogram configuration.
	 * One object per datatype.
	 * <code>null</code> for non-arrays.
	 */
	private final int[] histogram;

	/**
	 * For non-arrays acts as an ordinal instance counter.
	 * For arrays counts instances created via {@link #incrementArraySizeAndCount(int, long)} and this count
	 * tracks the count of the smallest arrays. For arrays without detailed histograms
	 * (when {@code histogram.length == 0}) this is the only tracked count.
	 */
	private int count;

	/**
	 * Total size of all allocated array instances. Always zero for non-arrays.
	 */
	private long size;

	/**
	 * Instance counter for arrays of specific lengths (as specified in {@link #histogram}).
	 * <code>null</code> for non-arrays and for arrays with empty histogram (when {@code histogram.length == 0})
	 */
	private final int[] histogramCounts;

	/**
	 * Number of children.
	 */
	private volatile int childrenCount;

	/**
	 * Children hash-indexed map. <code>null</code> when there are no children.
	 */
	private T[] children;

	public IndexMap(int location, int[] histogram) {
		this.location = location;
		this.histogram = histogram;
		this.histogramCounts = histogram == null || histogram.length == 0 ? null : new int[histogram.length];
	}

	public int getLocation() {
		return location;
	}

	public int getHistogramLength() {
		return histogram.length;
	}

	public int[] getHistogram() {
		return histogram;
	}

	public int getChildrenCount() {
		return childrenCount;
	}

	// will not crash without synchronization, but may return null
	public T getChildUnsync(int loc) {
		T[] children = this.children; // atomic read (non-volatile)
		if (children == null)
			return null;
		int i = loc & (children.length - 1); // always power of 2 in length
		T child;
		while ((child = children[i]) != null) {
			if (child.getLocation() == loc)
				return child;
			if (i == 0)
				i = children.length;
			i--;
		}
		return null;
	}

	public IndexMap registerChild(int loc) {
		IndexMap result = getChildUnsync(loc);
		if (result == null)
			result = registerChildSlowPath(loc);
		return result;
	}

	@SuppressWarnings({"SynchronizationOnLocalVariableOrMethodParameter", "unchecked"})
	private synchronized IndexMap registerChildSlowPath(int loc) {
		IndexMap result = getChildUnsync(loc);
		if (result == null) {
			result = new IndexMap(loc, getHistogram());
			putNewChildUnsync((T)result);
		}
		return result;
	}

	private void putInternal(T[] children, T child) {
		int i = child.getLocation() & (children.length - 1); // always power of 2 in length
		while (children[i] != null) {
			if (i == 0)
				i = children.length;
			i--;
		}
		children[i] = child;
	}

	// needs external synchronization on _this_ and external check that the child does not exist yet
	@SuppressWarnings("unchecked")
	public void putNewChildUnsync(T child) {
		T[] children = this.children;
		if (children == null)
			this.children = children = (T[])new IndexMap[4];
		else if (childrenCount >= children.length / 2)
			this.children = children = rehashChildren(children);
		putInternal(children, child);
		childrenCount++;
	}

	@SuppressWarnings("unchecked")
	private T[] rehashChildren(T[] oldChildren) {
		int n = oldChildren.length;
		T[] newChildren = (T[])new IndexMap[n * 2];
		for (T child : oldChildren)
			if (child != null)
				putInternal(newChildren, child);
		return newChildren;
	}

	public void visitChildren(IndexMapVisitor visitor) {
		IndexMap[] children = this.children; // atomic read (non-volatile)
		if (children != null)
			for (IndexMap child : children)
				if (child != null)
					visitor.acceptChild(child);
	}

	public int getCount() {
		return count;
	}

	public long getSize() {
		return size;
	}

	/**
	 * Returns {@code true} for array data types.
	 */
	public boolean hasHistogram() {
		return histogram != null;
	}

	public int takeCount() {
		int val;
		do {
			val = count;
		} while (!UnsafeHolder.UNSAFE.compareAndSwapInt(this, COUNT_OFFSET, val, 0));
		return val;
	}

	public long takeSize() {
		long val;
		do {
			val = size;
		} while (!UnsafeHolder.UNSAFE.compareAndSwapLong(this, SIZE_OFFSET, val, 0));
		return val;
	}

	public int takeHistogramCount(int i) {
		int val;
		do {
			val = histogramCounts[i];
		} while (!UnsafeHolder.UNSAFE.compareAndSwapInt(histogramCounts,
				INT_ARRAY_BASE_OFFSET + i * INT_ARRAY_INDEX_SCALE, val, 0));
		return val;
	}

	public void incrementCount() {
		int val;
		do {
			val = count;
		} while (!UnsafeHolder.UNSAFE.compareAndSwapInt(this, COUNT_OFFSET, val, val + 1));
	}

	private void incrementSize(long size) {
		long val;
		do {
			val = this.size;
		} while (!UnsafeHolder.UNSAFE.compareAndSwapLong(this, SIZE_OFFSET, val, val + size));
	}

	private void incrementHistogramCount(int i) {
		int val;
		do {
			val = histogramCounts[i];
		} while (!UnsafeHolder.UNSAFE.compareAndSwapInt(histogramCounts,
				INT_ARRAY_BASE_OFFSET + i * INT_ARRAY_INDEX_SCALE, val, val + 1));
	}

	public void incrementArraySizeAndCount(int length, long size) {
		incrementSize(size);
		if (histogramCounts == null || length < histogram[0]) {
			// fast path -- no histogram is specified for this array type or the length of the array is in the smallest bracket
			incrementCount();
			return;
		}
		incrementArraySizeAndCountSlowPath(length);
	}

	private void incrementArraySizeAndCountSlowPath(int length) {
		int last = histogram.length - 1;
		for (int i = 0; i < last; i++) {
			if (length <= histogram[i + 1]) {
				incrementHistogramCount(i);
				return;
			}
		}
		incrementHistogramCount(last);
	}

	public boolean isOverflowThreshold() {
		if (count >= COUNT_OVERFLOW_THRESHOLD)
			return true;
		int[] histogramCounts = this.histogramCounts;
		if (histogramCounts != null)
			for (int cnt : histogramCounts)
				if (cnt >= COUNT_OVERFLOW_THRESHOLD)
					return true;
		IndexMap[] children = this.children; // atomic read (non-volatile)
		if (children != null)
			for (IndexMap child : children)
				if (child != null && child.isOverflowThreshold())
					return true;
		return false;
	}
}
