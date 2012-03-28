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

package com.devexperts.aprof.util;

import java.util.Iterator;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Roman Elizarov
 */
public final class IndexMap implements Iterable<Integer> {
    private final int index;

    /**
     * Class histogram configuration.
     * One object per datatype.
     * <code>null</code> for non-arrays.
     */
    private final int[] histogram;

    /**
     * For non-arrays acts as an ordinal instance counter.
     * For arrays counts instances created via {@link #increment(int size)}.
     */
    private final AtomicInteger counter;

    /**
     * Total size of all instances.
     */
    private final AtomicInteger size;

    /**
     * Instance counter for arrays of specific lengths (as specified in {@link #histogram}).
     * <code>null</code> for non-arrays.
     */
    private final AtomicInteger[] counters;

    private final FastIntObjMap<IndexMap> items = new FastIntObjMap<IndexMap>();

    public IndexMap(int index, int[] histogram) {
        this.index = index;
        this.histogram = histogram;
        this.counter = new AtomicInteger();
        if (histogram != null) {
            this.size = new AtomicInteger();
            this.counters = new AtomicInteger[histogram.length + 1];
            for (int i = 0; i < counters.length; i++) {
                this.counters[i] = new AtomicInteger();
            }
        } else {
            this.size = null;
            this.counters = null;
        }
	}

    public int getIndex() {
        return index;
    }

    public int[] getHistogram() {
        return histogram;
    }

    public IndexMap get(int key) {
		return items.get(key);
	}

    /* needs external synchronization */
	public void put(int key, IndexMap value) {
        items.put(key, value);
	}

    public int size() {
        return items.size();
    }

    public Iterator<Integer> iterator() {
        return items.iterator();
    }

    public AtomicInteger getCounter() {
        return counter;
    }

    public AtomicInteger[] getCounters() {
        return counters;
    }

    public AtomicInteger getSize() {
        return size;
    }

    public void increment() {
        this.counter.incrementAndGet();
    }

    public void increment(int size) {
        this.counter.incrementAndGet();
        this.size.addAndGet(size);
    }

    public void increment(int length, int size) {
        int idx = getHistogramIndex(length);
        this.counters[idx].incrementAndGet();
        this.size.addAndGet(size);
    }

    private int getHistogramIndex(int length) {
        for (int i = 0; i < histogram.length; i++) {
            if (length <= histogram[i]) {
                return i;
            }
        }
        return histogram.length;
    }
}