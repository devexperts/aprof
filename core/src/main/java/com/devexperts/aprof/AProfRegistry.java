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

import com.devexperts.aprof.util.*;

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
	private static final StringIndexer datatype_names = new StringIndexer();

	/* datatype infos are created at transformation time */
	private static final Object datatype_infos_sync = new Object();
	private static volatile DatatypeInfo[] datatype_infos = new DatatypeInfo[1024];

	/* indexes are created at any time */
	private static final AtomicInteger last_root_index = new AtomicInteger();
	private static final Object root_indexes_sync = new Object();
	private static volatile IndexMap[] root_indexes = new IndexMap[1024];

	private static final String UNKNOWN = "<unknown>";
	public static final int UNKNOWN_LOC = registerLocation(UNKNOWN);
	private static final int MAKE_SNAPSHOT_LOC = registerLocation(AProfRegistry.class.getCanonicalName() + ".makeSnapshot");

	private static Configuration config;
	private static ClassNameResolver class_name_resolver;

	static void init(Configuration config, ClassNameResolver resolver) {
		if (config == null)
			throw new IllegalArgumentException("Aprof arguments must be specified");
		if (resolver == null)
			throw new IllegalArgumentException("Class-name resolver must be specified");
		AProfRegistry.config = config;
		AProfRegistry.class_name_resolver = resolver;

		registerDatatypeInfo(Object.class.getCanonicalName());
		registerDatatypeInfo(IndexMap.class.getCanonicalName());
		registerDatatypeInfo(FastIntObjMap.class.getCanonicalName());
	}

	public static boolean isInternalLocation(String name) {
		name = name.replace('/', '.');
		if (name.startsWith("java.lang.ThreadLocal")) {
			return true;
		}
		if (name.startsWith("com.devexperts.aprof.")) {
			return true;
		}
		return false;
	}

	public static String normalize(String string) {
		int pos1 = string.indexOf(PROXY_CLASS_TOKEN);
		if (pos1 >= 0) {
			pos1 += PROXY_CLASS_TOKEN.length();
			int pos2 = pos1;
			while (pos2 < string.length() && Character.isDigit(string.charAt(pos2))) {
				pos2++;
			}
			string = string.substring(0, pos1) + string.substring(pos2);
		} else if (config != null) {
			for (String name : config.getAggregatedClasses()) {
				if (string.startsWith(name)) {
					int pos = name.length();
					while (pos < string.length() && Character.isDigit(string.charAt(pos))) {
						pos++;
					}
					string = name + string.substring(pos);
					break;
				}
			}
		}
		return new String(string.toCharArray());
	}

	public static Configuration getConfiguration() {
		return config;
	}

	static int getLocationCount() {
		return last_root_index.get();
	}

	private static String resolveClassName(String datatype) {
		return class_name_resolver.resolve(datatype);
	}

	// allocates memory during class transformation only
	public static int registerLocation(String location) {
		int loc = locations.get(location);
		if (loc == 0) {
			loc = locations.register(normalize(location));
		}
		return loc;
	}

	// allocates memory during class transformation only???
	public static DatatypeInfo getDatatypeInfo(String datatype) {
		int id = datatype_names.get(datatype);
		if (id == 0) {
			return null;
		}
		DatatypeInfo datatype_info = datatype_infos[id];
		if (datatype_info == null) {
			return getDatatypeInfoImpl(id);
		}
		return datatype_info;
	}

	// allocates memory during class transformation only
	public static DatatypeInfo registerDatatypeInfo(String datatype) {
		int id = datatype_names.get(datatype);
		if (id == 0) {
			id = datatype_names.register(normalize(datatype));
			return createDatatypeInfo(id);
		}
		DatatypeInfo datatype_info = datatype_infos[id];
		if (datatype_info == null) {
			return getDatatypeInfoImpl(id);
		}
		return datatype_info;
	}

	private static DatatypeInfo getDatatypeInfo(int id) {
		DatatypeInfo datatype_info = datatype_infos[id];
		if (datatype_info == null) {
			return getDatatypeInfoImpl(id);
		}
		return datatype_info;
	}

	private static DatatypeInfo getDatatypeInfoImpl(int id) {
		synchronized (datatype_infos_sync) {
			return datatype_infos[id];
		}
	}

	private static DatatypeInfo createDatatypeInfo(int id) {
		synchronized (datatype_infos_sync) {
			ensureDatatypeIndexCapacity(id);
			DatatypeInfo datatype_info = datatype_infos[id];
			if (datatype_info == null) {
				String datatype = datatype_names.get(id);
				if (datatype.startsWith("[")) {
					datatype = resolveClassName(datatype);
					IndexMap map = new IndexMap(id, config.getHistogram(datatype));
					datatype_info = new DatatypeInfo(datatype, map);
				} else {
					datatype = resolveClassName(datatype);
					IndexMap map = new IndexMap(id, null);
					datatype_info = new DatatypeInfo(datatype, map);
				}
				datatype_infos[id] = datatype_info;
			}
			return datatype_info;
		}
	}

	// allocates memory during class transformation only
	// requires synchronization on datatype_infos
	private static void ensureDatatypeIndexCapacity(int last_id) {
		int length = datatype_infos.length;
		if (length <= last_id) {
			while (length <= last_id) {
				length *= 2;
			}
			DatatypeInfo[] new_datatype_infos = new DatatypeInfo[length];
			System.arraycopy(datatype_infos, 0, new_datatype_infos, 0, datatype_infos.length);
			datatype_infos = new_datatype_infos;
		}
	}

	static IndexMap getRootIndex(int id) {
		IndexMap result = id < root_indexes.length ? root_indexes[id] : null;
		if (result == null) {
			result = getRootIndexImpl(id);
		}
		return result;
	}

	private static IndexMap getRootIndexImpl(int id) {
		synchronized (root_indexes_sync) {
			return root_indexes[id];
		}
	}

	// allocates memory during class transformation and reflection calls
	public static IndexMap registerRootIndex(DatatypeInfo datatype_info, int loc) {
		IndexMap datatype_map = datatype_info.getIndex();
		IndexMap root_map = datatype_map.get(loc);
		if (root_map == null) {
			//noinspection SynchronizationOnLocalVariableOrMethodParameter
			synchronized (datatype_map) {
				root_map = datatype_map.get(loc);
				if (root_map == null) {
					int root_index = last_root_index.incrementAndGet();
					root_map = new IndexMap(root_index, datatype_map.getHistogram());
					datatype_map.put(loc, root_map);
					synchronized (root_indexes_sync) {
						ensureRootIndexCapacity(root_index);
						root_indexes[root_index] = root_map;
					}
				}
			}
		}
		return root_map;
	}

	// allocates memory during class transformation and reflection calls
	private static void ensureRootIndexCapacity(int last_id) {
		if (root_indexes.length <= last_id) {
			synchronized (root_indexes_sync) {
				int length = root_indexes.length;
				if (length <= last_id) {
					while (length <= last_id) {
						length *= 2;
					}
					IndexMap[] new_indexes = new IndexMap[length];
					System.arraycopy(root_indexes, 0, new_indexes, 0, root_indexes.length);
					root_indexes = new_indexes;
				}
			}
		}
	}

	// allocates memory during class transformation only
	public static int registerAllocationPoint(String datatype, String location) {
		DatatypeInfo datatype_info = registerDatatypeInfo(datatype);
		int loc = registerLocation(location);
		return registerRootIndex(datatype_info, loc).getIndex();
	}

	// TODO: can allocate memory
	private static IndexMap putLocation(IndexMap map, int loc) {
		IndexMap result = map.get(loc);
		if (result == null) {
			//noinspection SynchronizationOnLocalVariableOrMethodParameter
			synchronized (map) {
				result = map.get(loc);
				if (result == null) {
					result = new IndexMap(-1, map.getHistogram());
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
	static IndexMap getDetailedIndex(String datatype, int loc) {
		DatatypeInfo datatype_info = registerDatatypeInfo(datatype);
		return registerRootIndex(datatype_info, loc);
	}

	// can allocate memory during execution
	static IndexMap getDetailedIndex(LocationStack stack, int index) {
		IndexMap map = getRootIndex(index);
		if (stack.internal_invoked_method_count > 0) {
			return putLocation(map, stack.internal_invoked_method_loc);
		}
		int loc1 = stack.invoked_method_loc;
		int loc2 = stack.invocation_point_loc;
		if (loc2 != UNKNOWN_LOC) {
			map = putLocation(map, loc1);
			map = putLocation(map, loc2);
		} else if (loc1 != UNKNOWN_LOC) {
			map = putLocation(map, loc1);
		}
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
		int size = datatype_names.size();
		DatatypeInfo[] datatypes = new DatatypeInfo[size];
		int count = 0;
		for (int i = 0; i < size; i++) {
			DatatypeInfo datatype_info = getDatatypeInfo(i);
			if (datatype_info == null) {
				continue;
			}
			datatypes[count++] = datatype_info;
		}
		QuickSort.sort(datatypes, 0, count, DatatypeInfo.COMPARATOR_NAME);
		ss.ensureCapacity(count);
		int idx = 0;
		for (int i = 0 ; i < count; i++) {
			DatatypeInfo datatype_info = datatypes[i];
			String name = datatype_info.getName();
			IndexMap map = datatype_info.getIndex();
			int[] histogram = map.getHistogram();
			int histo_length = histogram == null ? 0 : histogram.length + 1;
			Snapshot cs = ss.get(idx = ss.move(idx, name), name, histo_length);
			makeSnapshotRec(cs, map, datatype_info.getSize(), new Snapshot(null, histo_length), new Snapshot(null, histo_length));
			ss.add(cs);
		}
	}

	private static void makeSnapshotRec(Snapshot list, IndexMap map, int class_size, Snapshot unknown, Snapshot total) {
		list.ensureCapacity(map.size());
		list.clear();

		Snapshot temp = total != null ? total : unknown;
		AtomicInteger acounter = map.getCounter();
		AtomicInteger asize = map.getSize();
		AtomicInteger[] acounters = map.getCounters();
		if (acounters == null) {
			long count = acounter.getAndSet(0);
			long size = (count * class_size) << ArraySizeHelper.SIZE_SHIFT;
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
			IndexMap unknown_map = map.get(UNKNOWN_LOC);
			if (unknown_map != null) {
				Snapshot child_list = list.get(list.move(0, UNKNOWN), UNKNOWN);
				makeSnapshotRec(child_list, unknown_map, class_size, unknown, null);
				list.add(child_list);
				unknown.clear();
			} else if (!unknown.isEmpty()) {
				Snapshot child_list = list.get(list.move(0, UNKNOWN), UNKNOWN);
				child_list.add(unknown);
				list.add(child_list);
				unknown.clear();
			}
			// unknown is empty now
			for (int key : map) {
				if (key == UNKNOWN_LOC) {
					continue;
				}
				String id = locations.get(key);
				IndexMap child_map = map.get(key);
				Snapshot child_list = list.get(list.move(0, id), id);
				makeSnapshotRec(child_list, child_map, class_size, unknown, null);
				if (child_list.isEmpty()) {
					continue;
				}
				if (!id.endsWith(CLONE_SUFFIX)) {
					// do not count "clone" calls because they do not invoke constructor
					list.add(child_list);
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
		boolean unknowns_only = true;
		for (int i = 0; i < list.getUsed(); i++) {
			Snapshot item = list.getItem(i);
			if (item.isEmpty()) {
				continue;
			}
			if (UNKNOWN.equals(item.getId())) {
				unknown = item;
			} else {
				compactUnknowns(item);
				unknowns_only = false;
			}
		}
		if (unknown == null) {
			return !unknowns_only;
		}
		if (compactUnknowns(unknown)) {
			return true;
		}
		if (unknowns_only) {
			unknown.clear();
		}
		return !unknowns_only;
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

	public static long incrementTime(long time_period) {
		return time.addAndGet(time_period);
	}

	//=================== DIRECT CLONE ====================

	// called during class transformation only
	public static void addDirectCloneClass(String name) {
		registerDatatypeInfo(name).setDirectClone(true);
	}

	// called during class transformation only
	public static void removeDirectCloneClass(String name) {
		getDatatypeInfo(name).setDirectClone(false);
	}

	public static boolean isDirectCloneClass(String name) {
		DatatypeInfo datatype_info = getDatatypeInfo(name);
		return datatype_info != null && datatype_info.isDirectClone();
	}
}
