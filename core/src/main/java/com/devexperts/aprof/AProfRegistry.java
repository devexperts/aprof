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

import com.devexperts.aprof.dump.SnapshotDeep;
import com.devexperts.aprof.dump.SnapshotShallow;
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

	/**
	 * Allocated and reused in makeSnapshotInternal.
	 */
	private static DatatypeInfo[] SORTED_DATATYPES;

	/**
	 * Temporary object to collect unknowns.
	 */
	private static SnapshotShallow UNKNOWN_TEMP;

	/**
	 * Temporary object to collect totals per data-type.
	 */
	private static SnapshotShallow DATATYPE_TOTAL_TEMP;

	/**
	 * Temporary object to collect totals for clone invocations.
	 */
	private static SnapshotShallow CLONE_TOTAL_TEMP;

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

		// allocate memory for temp snapshots
		int histoCountsLength = config.getMaxHistogramLength() + 1;
		UNKNOWN_TEMP = new SnapshotShallow(null, histoCountsLength);
		DATATYPE_TOTAL_TEMP = new SnapshotShallow(null, histoCountsLength);
		CLONE_TOTAL_TEMP = new SnapshotShallow(null, histoCountsLength);
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

	/**
	 * Adds current snapshot information to <code>ss</code> and clears internal counters.
	 */
	public static void takeSnapshot(SnapshotDeep ss) {
		ss.sortChildrenDeep(SnapshotDeep.COMPARATOR_NAME);
		takeSnapshotInternalSync(ss);
		compactUnknowns(ss);
	}

	private static synchronized void takeSnapshotInternalSync(SnapshotDeep ss) {
		int size = DATATYPE_NAMES.size();
		if (SORTED_DATATYPES == null || SORTED_DATATYPES.length < size)
			SORTED_DATATYPES = new DatatypeInfo[(int)(1.5 * size)]; // reserve for the future growth
		int count = 0;
		for (int i = 0; i < size; i++) {
			DatatypeInfo datatypeInfo = getDatatypeInfo(i);
			if (datatypeInfo == null)
				continue;
			SORTED_DATATYPES[count++] = datatypeInfo;
		}
		QuickSort.sort(SORTED_DATATYPES, 0, count, DatatypeInfo.COMPARATOR_NAME);
		ss.ensureChildrenCapacity(count);
		ss.clearShallow(); // will recompute sum here
		int idx = 0;
		for (int i = 0 ; i < count; i++) {
			// process datatype
			DatatypeInfo datatypeInfo = SORTED_DATATYPES[i];
			String name = datatypeInfo.getName();
			IndexMap map = datatypeInfo.getIndex();
			int classSize = datatypeInfo.getSize();
			int[] histogram = map.getHistogram();
			int histoCountsLength = histogram == null ? 0 : histogram.length + 1;
			boolean trackClassUnknown = config.isUnknown() && !datatypeInfo.isArray();
			// find child snapshot corresponding to this datatype
			SnapshotDeep cs = ss.getChild(idx = ss.findChild(idx, name), name, histoCountsLength);
			// take snapshot
			assert DATATYPE_TOTAL_TEMP.isEmpty();
			takeSnapshotShallow(DATATYPE_TOTAL_TEMP, map, classSize);
			// datatypes must allays have children -- location from were allocations were performed
			assert CLONE_TOTAL_TEMP.isEmpty();
			takeSnapshotDeepOnly(cs, map, classSize, trackClassUnknown ? CLONE_TOTAL_TEMP : null);
			// create unknown node for datatype if tracked them (was enabled in config for non-array datatypes)
			if (trackClassUnknown) {
				DATATYPE_TOTAL_TEMP.subShallow(cs); // compute unknown remainder
				DATATYPE_TOTAL_TEMP.ensurePositive();
				addToUnknown(cs, DATATYPE_TOTAL_TEMP);
				cs.addShallow(CLONE_TOTAL_TEMP); // add clone totals to overall totals
				DATATYPE_TOTAL_TEMP.clearShallow();
				CLONE_TOTAL_TEMP.clearShallow();
			} else
				assert DATATYPE_TOTAL_TEMP.isEmpty(); // otherwise, datatype counters should not have anything in them
			 // add this datatype to the total sum
			ss.addShallow(cs);
		}
	}

	private static void takeSnapshotShallow(SnapshotShallow ss, IndexMap map, int classSize) {
		long count = map.takeCount(); // SIC! Its long to avoid overflows
		if (map.hasHistogram()) {
			// Array (dynamically tracked sum size with histograms)
			long size = ((long)map.takeSize()) << AProfSizeUtil.SIZE_SHIFT;
			ss.add(count, size);
			for (int i = 0; i < map.getHistogramLength(); i++)
				ss.addHistoCount(i, map.takeHistogramCount(i));
		} else {
			// Regular object (fixed size)
			long size = (count * classSize) << AProfSizeUtil.SIZE_SHIFT;
			ss.add(count, size);
		}
	}

	/**
	 * Recursively adds snapshot from {@code map} to {@code ss} for a class of a known size {@code classSize}
	 * (which is zero for arrays or when size is not being tracked).
	 */
	private static void takeSnapshotDeepOnly(SnapshotDeep ss, IndexMap map, int classSize, SnapshotShallow cloneTotal) {
		ss.ensureChildrenCapacity(map.size());
		ss.clearShallow(); // will recompute sum here from children

		for (IntIterator it = map.iterator(); it.hasNext();) {
			int key = it.next();
			String name = LOCATIONS.get(key);
			IndexMap childMap = map.get(key);
			SnapshotDeep cs = ss.getChild(ss.findChild(0, name), name);
			if (childMap.size() > 0) {
				// if child has children, then move its shallow snapshot to UNKNOWN and go recursively into its children
				assert UNKNOWN_TEMP.isEmpty();
				takeSnapshotShallow(UNKNOWN_TEMP, childMap, classSize);
				addToUnknown(cs, UNKNOWN_TEMP);
				UNKNOWN_TEMP.clearShallow();
				takeSnapshotDeepOnly(cs, childMap, classSize, null);
			} else {
				// child has no children of its own -- just take its shallow snapshot
				takeSnapshotShallow(cs, childMap, classSize);
			}
			if (name.endsWith(CLONE_SUFFIX) && cloneTotal != null) {
				// count clone invocation separately and do not count them in overall totals
				// because they do not invoke constructor when unknown tracking is enabled
				cloneTotal.addShallow(cs);
			} else {
				ss.addShallow(cs);
			}
		}
	}

	private static void addToUnknown(SnapshotDeep ss, SnapshotShallow unknown) {
		if (unknown.isEmpty())
			return;
		SnapshotDeep cs = ss.getChild(ss.findChild(0, UNKNOWN), UNKNOWN);
		cs.addShallow(unknown);
		ss.addShallow(cs);
	}

	/**
	 * Compacts unknown nodes and returns {@code true} when the snapshot
	 * contains non-unknown children.
	 */
	private static boolean compactUnknowns(SnapshotDeep ss) {
		SnapshotDeep unknown = null;
		boolean unknownsOnly = true;
		for (int i = 0; i < ss.getUsed(); i++) {
			SnapshotDeep cs = ss.getChild(i);
			if (cs.isEmpty())
				continue;
			if (UNKNOWN.equals(cs.getName())) {
				unknown = cs;
			} else {
				compactUnknowns(cs);
				unknownsOnly = false;
			}
		}
		if (unknown == null)
			return !unknownsOnly;
		if (compactUnknowns(unknown))
			return true;
		if (unknownsOnly)
			unknown.clearShallow();
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
