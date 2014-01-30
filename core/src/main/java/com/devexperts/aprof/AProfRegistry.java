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

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import com.devexperts.aprof.dump.Snapshot;
import com.devexperts.aprof.util.*;

/**
 * @author Roman Elizarov
 * @author Dmitry Paraschenko
 */
public class AProfRegistry {
	private static final String PROXY_CLASS_TOKEN = "$Proxy";

	public static final String ARRAY_NEWINSTANCE_SUFFIX = "#";
	public static final String CLONE_SUFFIX = "*";

	private static final int OVERFLOW_THRESHOLD = 1 << 30;

	/**
	 * Locations are created at transformation time
	 */
	private static final StringIndexer LOCATIONS = new StringIndexer();

	/**
	 * Datatypes are created at transformation time.
	 */
	private static final StringIndexer DATATYPE_NAMES = new StringIndexer();

	/**
	 * Datatype infos are created at transformation time.
	 */
	private static final FastArrayList<DatatypeInfo> DATATYPE_INFOS = new FastArrayList<DatatypeInfo>();

	private static final AtomicInteger LAST_ROOT_INDEX = new AtomicInteger();

	/**
	 * Indexes can be created at any time.
	 */
	private static final FastArrayList<IndexMap> ROOT_INDEXES = new FastArrayList<IndexMap>();

	private static final String UNKNOWN = "<unknown>";

	public static final int UNKNOWN_LOC = registerLocation(UNKNOWN);

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
		return locationClass.startsWith("java.lang.ThreadLocal") ||
				locationClass.startsWith("com.devexperts.aprof.") &&
						!locationClass.startsWith("com.devexperts.aprof.transformer.") &&
						!locationClass.startsWith("com.devexperts.aprof.dump.");
	}

	public static boolean isNormal(String cname) {
		int pos1 = cname.indexOf(PROXY_CLASS_TOKEN);
		if (pos1 >= 0)
			return false;
		if (config != null)
			for (String name : config.getAggregatedClasses())
				if (cname.startsWith(name))
					return false;
		return true;
	}

	// converts fully qualified dot-separated class name (cname) to "locationClass"
	// when isNormal(cname) == true, then normalize(cname).equals(cname)
	// also isNormal(normalize(cname)) is always true
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
		}
		if (config != null) {
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

	public static int getLocationCount() {
		return LAST_ROOT_INDEX.get();
	}

	private static String resolveClassName(String datatype) {
		return classNameResolver.resolve(datatype);
	}

	// allocates memory during class transformation only
	public static int registerLocation(String location) {
		int loc = LOCATIONS.get(location);
		if (loc == 0)
			loc = LOCATIONS.register(location);
		return loc;
	}

	// allocates memory during class transformation only???
	public static DatatypeInfo getDatatypeInfo(String cname) {
		int id = DATATYPE_NAMES.get(cname);
		if (id == 0)
			return null;
		return DATATYPE_INFOS.getSafely(id);
	}

	// allocates memory during class transformation only
	public static DatatypeInfo registerDatatypeInfo(String locationClass) {
		int id = DATATYPE_NAMES.get(locationClass);
		if (id == 0) {
			id = DATATYPE_NAMES.register(locationClass);
			return createDatatypeInfo(id);
		}
		return DATATYPE_INFOS.getSafely(id);
	}

	private static DatatypeInfo getDatatypeInfo(int id) {
		return DATATYPE_INFOS.getSafely(id);
	}

	private static DatatypeInfo createDatatypeInfo(int id) {
		synchronized (DATATYPE_INFOS) {
			DatatypeInfo datatypeInfo = DATATYPE_INFOS.getUnsync(id);
			if (datatypeInfo == null) {
				String datatype = DATATYPE_NAMES.get(id);
				if (datatype.startsWith("[")) {
					datatype = resolveClassName(datatype);
					IndexMap map = new IndexMap(UNKNOWN_LOC, id, config.getHistogram(datatype));
					datatypeInfo = new DatatypeInfo(datatype, map);
				} else {
					datatype = resolveClassName(datatype);
					IndexMap map = new IndexMap(UNKNOWN_LOC, id, null);
					datatypeInfo = new DatatypeInfo(datatype, map);
				}
				DATATYPE_INFOS.putUnsync(id, datatypeInfo);
			}
			return datatypeInfo;
		}
	}

	static IndexMap getRootIndex(int id) {
		return ROOT_INDEXES.getSafely(id);
	}

	// allocates memory during class transformation and reflection calls
	public static IndexMap registerRootIndex(DatatypeInfo datatypeInfo, int loc) {
		IndexMap datatypeMap = datatypeInfo.getIndex();
		IndexMap rootMap = datatypeMap.get(loc);
		if (rootMap == null)
			rootMap = registerRootIndexSlowPath(datatypeMap, loc);
		return rootMap;
	}

	private static IndexMap registerRootIndexSlowPath(IndexMap datatypeMap, int loc) {
		synchronized (datatypeMap) {
			IndexMap rootMap = datatypeMap.get(loc);
			if (rootMap == null) {
				int index = LAST_ROOT_INDEX.incrementAndGet();
				rootMap = new IndexMap(loc, index, datatypeMap.getHistogram());
				datatypeMap.put(loc, rootMap);
				synchronized (ROOT_INDEXES) {
					ROOT_INDEXES.putUnsync(index, rootMap);
				}
			}
			return rootMap;
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

	public static boolean isOverflowThreshold() {
		int n = getLocationCount();
		for (int i = 1; i <= n; i++) {
			IndexMap map = getRootIndex(i);
			if (map == null) {
				continue;
			}
			if (map.getCount() >= OVERFLOW_THRESHOLD)
				return true;
			int[] counters = map.getHistogramCounts();
			if (counters != null) {
				for (int cnt : counters)
					if (cnt >= OVERFLOW_THRESHOLD)
						return true;
			}
			if (map.getSize() >= OVERFLOW_THRESHOLD)
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
		ss.sort(Snapshot.COMPARATOR_ID);
		makeSnapshotInternal(ss);
		compactUnknowns(ss);
	}

	private static synchronized void makeSnapshotInternal(Snapshot ss) {
		int size = DATATYPE_NAMES.size();
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
		long count = map.takeCount(); // SIC! Its long to avoid overflows
		if (map.hasHistogram()) {
			long size = ((long)map.takeSize()) << AProfSizeUtil.SIZE_SHIFT;
			int n = map.getHistogramLength();
			long[] counts = new long[n];
			for (int i = 0; i < n; i++)
				counts[i] = map.takeHistogramCount(i);
			temp.add(count, size, counts);
		} else {
			long size = (count * classSize) << AProfSizeUtil.SIZE_SHIFT;
			temp.add(count, size);
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
			for (IntIterator it = map.iterator(); it.hasNext();) {
				int key = it.next();
				if (key == UNKNOWN_LOC)
					continue;
				String id = LOCATIONS.get(key);
				IndexMap childMap = map.get(key);
				Snapshot childList = list.get(list.move(0, id), id);
				makeSnapshotRec(childList, childMap, classSize, unknown, null);
				if (childList.isEmpty())
					continue;
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
