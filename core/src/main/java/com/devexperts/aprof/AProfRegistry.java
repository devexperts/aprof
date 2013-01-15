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

package com.devexperts.aprof;

import com.devexperts.aprof.util.FastIntObjMap;
import com.devexperts.aprof.util.IndexMap;
import com.devexperts.aprof.util.QuickSort;
import com.devexperts.aprof.util.StringIndexer;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author Roman Elizarov
 * @author Dmitry Paraschenko
 */
public class AProfRegistry {
	private static final String PROXY_CLASS_TOKEN = "$Proxy";

	public static final String ARRAY_NEWINSTANCE_SUFFIX = "#";
	public static final String CLONE_SUFFIX = "*";

	private static final int OVERFLOW_THRESHOLD = 1 << 30;

	/* locations are created at transformation time */
	private static final StringIndexer locations = new StringIndexer();
	/* datatypes are created at transformation time */
	private static final StringIndexer datatypeNames = new StringIndexer();

	/* datatype infos are created at transformation time */
	private static final Object datatypeInfosSync = new Object();
	private static volatile DatatypeInfo[] datatypeInfos = new DatatypeInfo[1024];

	/* indexes are created at any time */
	private static final AtomicInteger lastRootIndex = new AtomicInteger();
	private static final Object rootIndexesSync = new Object();
	private static volatile IndexMap[] rootIndexes = new IndexMap[1024];

	private static final String UNKNOWN = "<unknown>";
	public static final int UNKNOWN_LOC = registerLocation(UNKNOWN);
	private static final int MAKE_SNAPSHOT_LOC = registerLocation(AProfRegistry.class.getName() + ".makeSnapshot");

	private static Configuration config;
	private static ClassNameResolver classNameResolver;

	static void init(Configuration config, ClassNameResolver resolver) {
		if (config == null)
			throw new IllegalArgumentException("Aprof arguments must be specified");
		if (resolver == null)
			throw new IllegalArgumentException("Class-name resolver must be specified");
		AProfRegistry.config = config;
		AProfRegistry.classNameResolver = resolver;

		registerDatatypeInfo(Object.class.getName());
		registerDatatypeInfo(IndexMap.class.getName());
		registerDatatypeInfo(FastIntObjMap.class.getName());
	}

	public static boolean isInternalLocationClass(String locationClass) {
        return locationClass.startsWith("java.lang.ThreadLocal") || locationClass.startsWith("com.devexperts.aprof.");
    }

    // converts fully qualified dot-separated class name (cname) to "locationClass"
	public static String normalize(String cname) {
		int pos1 = cname.indexOf(PROXY_CLASS_TOKEN);
		if (pos1 >= 0) {
			pos1 += PROXY_CLASS_TOKEN.length();
			int pos2 = pos1;
			while (pos2 < cname.length() && Character.isDigit(cname.charAt(pos2))) {
				pos2++;
			}
            // snip $ProxyXXX number
			return cname.substring(0, pos1) + cname.substring(pos2);
		} else if (config != null) {
			for (String name : config.getAggregatedClasses()) {
				if (cname.startsWith(name)) {
					int pos = name.length();
					while (pos < cname.length() && Character.isDigit(cname.charAt(pos))) {
						pos++;
					}
                    // Snip number after aggregated class name
					return name + cname.substring(pos);
				}
			}
		}
        // Nothing to snip.
        // But we recreate string to trim the baggage that came with string,
        // because those strings comes from ASM which allocates them out of big char[] chunks (really!?)
		return new String(cname);
	}

	public static Configuration getConfiguration() {
		return config;
	}

	static int getLocationCount() {
		return lastRootIndex.get();
	}

	private static String resolveClassName(String datatype) {
		return classNameResolver.resolve(datatype);
	}

	// allocates memory during class transformation only
	public static int registerLocation(String location) {
		int loc = locations.get(location);
		if (loc == 0)
			loc = locations.register(location);
		return loc;
	}

	// allocates memory during class transformation only???
	public static DatatypeInfo getDatatypeInfo(String cname) {
		int id = datatypeNames.get(cname);
		if (id == 0)
			return null;
		DatatypeInfo datatypeInfo = datatypeInfos[id];
		if (datatypeInfo == null)
			return getDatatypeInfoImpl(id);
		return datatypeInfo;
	}

	// allocates memory during class transformation only
	public static DatatypeInfo registerDatatypeInfo(String locationClass) {
		int id = datatypeNames.get(locationClass);
		if (id == 0) {
			id = datatypeNames.register(locationClass);
			return createDatatypeInfo(id);
		}
		DatatypeInfo datatypeInfo = datatypeInfos[id];
		if (datatypeInfo == null)
			return getDatatypeInfoImpl(id);
		return datatypeInfo;
	}

	private static DatatypeInfo getDatatypeInfo(int id) {
		DatatypeInfo datatypeInfo = datatypeInfos[id];
		if (datatypeInfo == null) {
			return getDatatypeInfoImpl(id);
		}
		return datatypeInfo;
	}

	private static DatatypeInfo getDatatypeInfoImpl(int id) {
		synchronized (datatypeInfosSync) {
			return datatypeInfos[id];
		}
	}

	private static DatatypeInfo createDatatypeInfo(int id) {
		synchronized (datatypeInfosSync) {
			ensureDatatypeIndexCapacity(id);
			DatatypeInfo datatypeInfo = datatypeInfos[id];
			if (datatypeInfo == null) {
				String datatype = datatypeNames.get(id);
				if (datatype.startsWith("[")) {
					datatype = resolveClassName(datatype);
					IndexMap map = new IndexMap(UNKNOWN_LOC, id, config.getHistogram(datatype));
					datatypeInfo = new DatatypeInfo(datatype, map);
				} else {
					datatype = resolveClassName(datatype);
					IndexMap map = new IndexMap(UNKNOWN_LOC, id, null);
					datatypeInfo = new DatatypeInfo(datatype, map);
				}
				datatypeInfos[id] = datatypeInfo;
			}
			return datatypeInfo;
		}
	}

	// allocates memory during class transformation only
	// requires synchronization on datatypeInfos
	private static void ensureDatatypeIndexCapacity(int lastId) {
		int length = datatypeInfos.length;
		if (length <= lastId) {
			while (length <= lastId) {
				length *= 2;
			}
			DatatypeInfo[] new_datatypeInfos = new DatatypeInfo[length];
			System.arraycopy(datatypeInfos, 0, new_datatypeInfos, 0, datatypeInfos.length);
			datatypeInfos = new_datatypeInfos;
		}
	}

	static IndexMap getRootIndex(int id) {
		IndexMap result = id < rootIndexes.length ? rootIndexes[id] : null;
		if (result == null)
			result = getRootIndexImpl(id);
		return result;
	}

	private static IndexMap getRootIndexImpl(int id) {
		synchronized (rootIndexesSync) {
			return rootIndexes[id];
		}
	}

	// allocates memory during class transformation and reflection calls
	public static IndexMap registerRootIndex(DatatypeInfo datatypeInfo, int loc) {
		IndexMap datatypeMap = datatypeInfo.getIndex();
		IndexMap rootMap = datatypeMap.get(loc);
		if (rootMap == null) {
			//noinspection SynchronizationOnLocalVariableOrMethodParameter
			synchronized (datatypeMap) {
				rootMap = datatypeMap.get(loc);
				if (rootMap == null) {
					int index = lastRootIndex.incrementAndGet();
					rootMap = new IndexMap(loc, index, datatypeMap.getHistogram());
					datatypeMap.put(loc, rootMap);
					synchronized (rootIndexesSync) {
						ensureRootIndexCapacity(index);
						rootIndexes[index] = rootMap;
					}
				}
			}
		}
		return rootMap;
	}

	// allocates memory during class transformation and reflection calls
	private static void ensureRootIndexCapacity(int lastId) {
		if (rootIndexes.length <= lastId) {
			synchronized (rootIndexesSync) {
				int length = rootIndexes.length;
				if (length <= lastId) {
					while (length <= lastId) {
						length *= 2;
					}
					IndexMap[] newIndexes = new IndexMap[length];
					System.arraycopy(rootIndexes, 0, newIndexes, 0, rootIndexes.length);
					rootIndexes = newIndexes;
				}
			}
		}
	}

	// allocates memory during class transformation only
	public static int registerAllocationPoint(String cname, String location) {
		DatatypeInfo datatypeInfo = registerDatatypeInfo(normalize(cname));
		int loc = registerLocation(location);
		return registerRootIndex(datatypeInfo, loc).getIndex();
	}

	// TODO: can allocate memory
	private static IndexMap putLocation(IndexMap map, int loc) {
		IndexMap result = map.get(loc);
		if (result == null) {
			//noinspection SynchronizationOnLocalVariableOrMethodParameter
			synchronized (map) {
				result = map.get(loc);
				if (result == null) {
					result = new IndexMap(loc, -1, map.getHistogram());
					map.put(loc, result);
				}
			}
		}
		return result;
	}

	static boolean isOverflowThreshold() {
		int n = getLocationCount();
		for (int i = 1; i <= n; i++) {
			IndexMap map = getRootIndex(i);
			if (map == null) {
				continue;
			}
			AtomicInteger counter = map.getCounter();
			if (counter.get() >= OVERFLOW_THRESHOLD)
				return true;
			AtomicInteger[] counters = map.getCounters();
			if (counters != null) {
				for (AtomicInteger cnt : counters)
					if (cnt.get() >= OVERFLOW_THRESHOLD)
						return true;
			}
			AtomicInteger size = map.getSize();
			if (size != null && size.get() >= OVERFLOW_THRESHOLD)
				return true;
		}
		return false;
	}

	// TODO: can allocate memory during execution: new root because of reflection call
	// all data-types should be registered beforehand
	static IndexMap getDetailedIndex(String cname, int loc) {
		DatatypeInfo datatypeInfo = registerDatatypeInfo(normalize(cname));
		return registerRootIndex(datatypeInfo, loc);
	}

	// can allocate memory during execution
	static IndexMap getDetailedIndex(LocationStack stack, int index) {
		assert stack != null;
		IndexMap map = getRootIndex(index);
		if (stack.internal_invoked_method_count > 0)
			return putLocation(map, stack.internal_invoked_method_loc);
		int loc1 = stack.invoked_method_loc;
		int loc2 = stack.invocation_point_loc;
        if (loc1 != UNKNOWN_LOC && loc1 != map.getLocation())
            map = putLocation(map, loc1);
        if (loc2 != UNKNOWN_LOC)
            map = putLocation(map, loc2);
		return map;
	}


	//==================== SNAPSHOTS ======================

	/** Adds current snapshot information to <code>ss</code> and clears internal counters. */
	public static void makeSnapshot(Snapshot ss) {
		LocationStack.markInternalInvokedMethod(MAKE_SNAPSHOT_LOC);
		try {
			ss.sort(Snapshot.COMPARATOR_ID);
			makeSnapshotInternal(ss);
			compactUnknowns(ss);
		} finally {
			LocationStack.unmarkInternalInvokedMethod();
		}
	}

	private static synchronized void makeSnapshotInternal(Snapshot ss) {
		int size = datatypeNames.size();
		DatatypeInfo[] datatypes = new DatatypeInfo[size];
		int count = 0;
		for (int i = 0; i < size; i++) {
			DatatypeInfo datatypeInfo = getDatatypeInfo(i);
			if (datatypeInfo == null) {
				continue;
			}
			datatypes[count++] = datatypeInfo;
		}
		QuickSort.sort(datatypes, 0, count, DatatypeInfo.COMPARATOR_NAME);
		ss.ensureCapacity(count);
		int idx = 0;
		for (int i = 0 ; i < count; i++) {
			DatatypeInfo datatypeInfo = datatypes[i];
			String name = datatypeInfo.getName();
			IndexMap map = datatypeInfo.getIndex();
			int[] histogram = map.getHistogram();
			int histogramLength = histogram == null ? 0 : histogram.length + 1;
			Snapshot cs = ss.get(idx = ss.move(idx, name), name, histogramLength);
			makeSnapshotRec(cs, map, datatypeInfo.getSize(), new Snapshot(null, histogramLength), new Snapshot(null, histogramLength));
			ss.add(cs);
		}
	}

	private static void makeSnapshotRec(Snapshot list, IndexMap map, int classSize, Snapshot unknown, Snapshot total) {
		list.ensureCapacity(map.size());
		list.clear();

		Snapshot temp = total != null ? total : unknown;
		AtomicInteger acounter = map.getCounter();
		AtomicInteger asize = map.getSize();
		AtomicInteger[] acounters = map.getCounters();
		if (acounters == null) {
			long count = acounter.getAndSet(0);
			long size = (count * classSize) << ArraySizeHelper.SIZE_SHIFT;
			temp.add(count, size);
		} else {
			long count = acounter.getAndSet(0);
			long size = asize.getAndSet(0) << ArraySizeHelper.SIZE_SHIFT;
			long[] counts = new long[acounters.length];
			for (int i = 0; i < counts.length; i++) {
				counts[i] = acounters[i].getAndSet(0);
			}
			temp.add(count, size, counts);
		}
		if (map.size() == 0) {
			list.add(unknown);
			unknown.clear();
		} else {
			IndexMap unknownMap = map.get(UNKNOWN_LOC);
			if (unknownMap != null) {
				Snapshot childList = list.get(list.move(0, UNKNOWN), UNKNOWN);
				makeSnapshotRec(childList, unknownMap, classSize, unknown, null);
				list.add(childList);
				unknown.clear();
			} else if (!unknown.isEmpty()) {
				Snapshot childList = list.get(list.move(0, UNKNOWN), UNKNOWN);
				childList.add(unknown);
				list.add(childList);
				unknown.clear();
			}
			// unknown is empty now
			for (int key : map) {
				if (key == UNKNOWN_LOC) {
					continue;
				}
				String id = locations.get(key);
				IndexMap childMap = map.get(key);
				Snapshot childList = list.get(list.move(0, id), id);
				makeSnapshotRec(childList, childMap, classSize, unknown, null);
				if (childList.isEmpty()) {
					continue;
				}
				if (!id.endsWith(CLONE_SUFFIX)) {
					// do not count "clone" calls because they do not invoke constructor
					list.add(childList);
				}
			}
		}
		if (total != null) {
			total.sub(list);
			total.positive();
			if (!total.isEmpty()) {
				// now we should add <unknown> node
				list.add(total);
				list = list.get(list.move(0, UNKNOWN), UNKNOWN);
				list.add(total);
			}
		}
	}

	/** Returns whether the snapshot contains non-unknown subnode.*/
	private static boolean compactUnknowns(Snapshot list) {
		Snapshot unknown = null;
		boolean unknownsOnly = true;
		for (int i = 0; i < list.getUsed(); i++) {
			Snapshot item = list.getItem(i);
			if (item.isEmpty()) {
				continue;
			}
			if (UNKNOWN.equals(item.getId())) {
				unknown = item;
			} else {
				compactUnknowns(item);
				unknownsOnly = false;
			}
		}
		if (unknown == null) {
			return !unknownsOnly;
		}
		if (compactUnknowns(unknown)) {
			return true;
		}
		if (unknownsOnly) {
			unknown.clear();
		}
		return !unknownsOnly;
	}


	//=================== COUNT & TIME ====================

	private static final AtomicInteger cnt = new AtomicInteger();
	private static final AtomicLong time = new AtomicLong();

	public static int getCount() {
		return cnt.get();
	}

	public static long getTime() {
		return time.get();
	}

	public static int incrementCount() {
		return cnt.incrementAndGet();
	}

	public static long incrementTime(long timePeriod) {
		return time.addAndGet(timePeriod);
	}

	//=================== DIRECT CLONE ====================

	// called during class transformation only
	public static void addDirectCloneClass(String locationClass) {
		registerDatatypeInfo(locationClass).setDirectClone(true);
	}

	// called during class transformation only
	public static void removeDirectCloneClass(String locationClass) {
		getDatatypeInfo(locationClass).setDirectClone(false);
	}

	public static boolean isDirectCloneClass(String cname) {
		DatatypeInfo datatypeInfo = getDatatypeInfo(cname);
		return datatypeInfo != null && datatypeInfo.isDirectClone();
	}
}
