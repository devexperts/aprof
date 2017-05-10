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

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import com.devexperts.aprof.dump.SnapshotDeep;
import com.devexperts.aprof.dump.SnapshotShallow;
import com.devexperts.aprof.util.*;

/**
 * @author Roman Elizarov
 * @author Dmitry Paraschenko
 */
@Internal
public class AProfRegistry {
	private static final String PROXY_CLASS_TOKEN = "$Proxy";

	// locations that correspond to "clone" method invocation are internally marked with this suffix,
	// because they don't invoke constructor and need to be counted separately.
	private static final String OBJECT_CLONE_SUFFIX = ";via-clone";

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
	private static final FastArrayList<RootIndexMap> ROOT_INDEXES = new FastArrayList<RootIndexMap>();

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
	 * Pre-allocated visitor for each depth.
	 */
	private static SnapshotDeepVisitor[] SNAPSHOT_DEEP_VISITOR = new SnapshotDeepVisitor[4];

	public static final int UNKNOWN_LOC = registerLocation(SnapshotDeep.UNKNOWN);

	public static final boolean TRACK_TRANSFORM_DETAILS = Boolean.getBoolean("com.devexperts.aprof.trackTransformDetails");

	public static final String TRANSFORM_NAME = "com.devexperts.aprof.transformer.AProfTransformer.transform";

	public static final int TRANSFORM_LOC = registerLocation(TRANSFORM_NAME) ;

	private static Configuration config;

	static void init(Configuration config) {
		if (config == null)
			throw new IllegalArgumentException("Aprof arguments must be specified");
		AProfRegistry.config = config;

		registerDatatypeInfo(Object.class.getName());
		registerDatatypeInfo(IndexMap.class.getName());

		// allocate memory for temp snapshots
		int histoCountsLength = config.getMaxHistogramLength();
		UNKNOWN_TEMP = new SnapshotShallow(null, false, histoCountsLength);
		DATATYPE_TOTAL_TEMP = new SnapshotShallow(null, false, histoCountsLength);
	}

	/**
	 * Returns true for {@link Internal} location.
	 */
	public static boolean isInternalLocation(String locationClass) {
		return locationClass.startsWith("java.lang.ThreadLocal") ||
			locationClass.equals(AProfOps.class.getName()) ||
			locationClass.equals(AProfOpsInternal.class.getName()) ||
			locationClass.equals(AProfRegistry.class.getName()) ||
			locationClass.equals(IndexMap.class.getName()) ||
			locationClass.equals(LocationStack.class.getName()) ||
			locationClass.equals(LocationStackThreadLocal.class.getName()) ||
			locationClass.equals(FastArrayList.class.getName()) ||
			locationClass.equals(StringIndexer.class.getName());
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
		return cname;
	}

	public static int getLocationCount() {
		return LAST_ROOT_INDEX.get();
	}

	public static String resolveClassName(CharSequence internalName) {
		StringBuilder sb = resolveClassName(null, internalName);
		return sb != null ? sb.toString() : internalName.toString();
	}

	public static StringBuilder resolveClassName(StringBuilder sb, CharSequence internalName) {
		int aCnt = 0;
		while (aCnt < internalName.length() && internalName.charAt(aCnt) == '[')
			aCnt++;
		if (aCnt > 0 && sb == null)
			sb = new StringBuilder();
		int first = aCnt;
		if (aCnt > 0 && aCnt < internalName.length()) {
			// array ...
			char c = internalName.charAt(aCnt);
			first++;
			switch (c) {
			case 'V':
				sb.append("void");
				break;
			case 'Z':
				sb.append("boolean");
				break;
			case 'C':
				sb.append("char");
				break;
			case 'B':
				sb.append("byte");
				break;
			case 'S':
				sb.append("short");
				break;
			case 'I':
				sb.append("int");
				break;
			case 'J':
				sb.append("long");
				break;
			case 'F':
				sb.append("float");
				break;
			case 'D':
				sb.append("double");
				break;
			case 'L':
				break;
			default:
				assert false : internalName; // cannot happen
			}
		}
		if (sb == null) { // check if we need sb
			for (int i = first; i < internalName.length(); i++) {
				char c = internalName.charAt(i);
				if (c == '/' || c == ';') {
					sb = new StringBuilder();
					break;
				}
			}
		}
		if (sb != null) {
			for (int i = first; i < internalName.length(); i++) {
				char c = internalName.charAt(i);
				if (c != ';')
					sb.append(c == '/' ? '.' : c);
			}
		}
		// if aCnt > 0, then sb cannot be null (sic!)
		for (int i = 0; i < aCnt; i++)
			sb.append("[]");
		return sb;
	}

	public static String getLocationNameWithoutSuffix(String name) {
		return name.endsWith(OBJECT_CLONE_SUFFIX) ?
			name.substring(0, name.length() - OBJECT_CLONE_SUFFIX.length()) : name;
	}

	// allocates memory during class transformation only
	public static int registerLocation(String location) {
		int loc = LOCATIONS.get(location);
		if (loc == 0)
			loc = LOCATIONS.register(location);
		return loc;
	}

	// allocates memory during class transformation only
	public static int registerLocation(String location, boolean objectCloneInvocation) {
		return registerLocation(objectCloneInvocation ? location + OBJECT_CLONE_SUFFIX : location);
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
		assert !locationClass.startsWith("[") : locationClass;
		assert !locationClass.contains("/") : locationClass;
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
				datatypeInfo = new DatatypeInfo(datatype, datatype.endsWith("]") ? config.getHistogram(datatype) : null);
				DATATYPE_INFOS.putUnsync(id, datatypeInfo);
			}
			return datatypeInfo;
		}
	}

	static RootIndexMap getRootIndex(int id) {
		return ROOT_INDEXES.getSafely(id);
	}

	// allocates memory during class transformation and reflection calls
	public static RootIndexMap registerRootIndex(DatatypeInfo datatypeInfo, int loc) {
		IndexMap<RootIndexMap> datatypeMap = datatypeInfo.getIndex();
		RootIndexMap rootMap = datatypeMap.getChildUnsync(loc);
		if (rootMap == null)
			rootMap = registerRootIndexSlowPath(datatypeInfo, loc);
		return rootMap;
	}

	@SuppressWarnings("SynchronizationOnLocalVariableOrMethodParameter")
	private static RootIndexMap registerRootIndexSlowPath(DatatypeInfo datatypeInfo, int loc) {
		IndexMap<RootIndexMap> datatypeMap = datatypeInfo.getIndex();
		synchronized (datatypeMap) {
			RootIndexMap rootMap = datatypeMap.getChildUnsync(loc);
			if (rootMap == null) {
				int rootIndex = LAST_ROOT_INDEX.incrementAndGet();
				datatypeMap.putNewChildUnsync(rootMap =
					new RootIndexMap(loc, rootIndex, datatypeMap.getHistogram(), datatypeInfo));
				synchronized (ROOT_INDEXES) {
					ROOT_INDEXES.putUnsync(rootIndex, rootMap);
				}
			}
			return rootMap;
		}
	}

	// allocates memory during class transformation only
	public static int registerAllocationPoint(String className, String location) {
		DatatypeInfo datatypeInfo = registerDatatypeInfo(normalize(className));
		int loc = registerLocation(location);
		return registerRootIndex(datatypeInfo, loc).getRootIndex();
	}

	public static synchronized boolean isOverflowThreshold() {
		int n = DATATYPE_INFOS.length();
		for (int i = 0; i < n; i++) {
			DatatypeInfo datatypeInfo = DATATYPE_INFOS.getUnsync(i);
			if (datatypeInfo == null)
				continue;
			if (datatypeInfo.getIndex().isOverflowThreshold())
				return true;
		}
		return false;
	}

	// NOTE: It can allocate memory during execution: new root because of reflection call
	// all data-types should be registered beforehand
	static RootIndexMap getRootIndex(String cname, int loc) {
		return registerRootIndex(registerDatatypeInfo(normalize(cname)), loc);
	}

	// can allocate memory during execution
	static IndexMap getDetailedIndex(LocationStack stack, IndexMap map) {
		assert stack != null;
		int loc1 = stack.invoked_method_loc;
		int loc2 = stack.invocation_point_loc;
		if (loc1 != UNKNOWN_LOC && loc1 != map.getLocation())
			map = map.registerChild(loc1);
		if (loc2 != UNKNOWN_LOC)
			map = map.registerChild(loc2);
		// HotSpot will statically elide the following check when TRACK_TRANSFORM_DETAILS is off (by default)
		if (TRACK_TRANSFORM_DETAILS && stack.transform_loc != UNKNOWN_LOC && stack.transform_loc != loc2)
			map = map.registerChild(stack.transform_loc);
		return map;
	}

	//==================== SNAPSHOTS ======================

	/**
	 * Adds current snapshot information to <code>ss</code> and clears internal counters.
	 */
	public static void takeSnapshot(SnapshotDeep ss) {
		ss.sortChildrenDeep(SnapshotDeep.COMPARATOR_NAME);
		takeSnapshotInternalSync(ss);
	}

	// PRE-CONDITION: ss.sortChildrenDeep(SnapshotDeep.COMPARATOR_NAME)
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
		int idx = 0;
		for (int i = 0 ; i < count; i++) {
			// process datatype
			DatatypeInfo datatypeInfo = SORTED_DATATYPES[i];
			String name = datatypeInfo.getName();
			IndexMap map = datatypeInfo.getIndex();
			long classSize = datatypeInfo.getSize();
			if (classSize < 0) // If failed to compute size
				classSize = 0;
			int[] histogram = map.getHistogram();
			int histogramLength = histogram == null ? 0 : histogram.length;
			boolean trackClassUnknown = config.isUnknown() && !datatypeInfo.isArray();
			// find child snapshot corresponding to this datatype
			SnapshotDeep cs = ss.getOrCreateChildAt(idx =
				ss.findChildInSortedFrom(idx, name), name, datatypeInfo.isArray(), histogramLength);

			// NOTATION HERE FOR THIS DATA TYPE:
			//   a[t] = all recorded allocations up to time "t" (unknown and known locations, including from clone)
			//   c[t] = recorded allocations from "clone" up to time "t"
			//   k[t] = recorded allocations from known locations other than clone
			// So, a[t] - c[t] - k[t] is a total for unknown locations
			// where t = 0 for prev snapshot, t = 1 for this snapshot
			// NOW: cs = a[0]

			// take snapshot for data type itself (unknown locations)
			if (trackClassUnknown) {
				assert DATATYPE_TOTAL_TEMP.isEmpty();
				DATATYPE_TOTAL_TEMP.addShallow(cs);
				takeSnapshotShallow(DATATYPE_TOTAL_TEMP, map, classSize);
				// Data type shallow snapshot contains delta for a[t] - c[t], because all allocations but clone invoke Object.<init>
				// NOW: DATATYPE_TOTAL_TEMP = a[0] + (a[1] - c[1]) - (a[0] - c[0]) = a[1] - c[1] + c[0]

				// Remove the number of clones we had
				subCloneLocationsShallow(cs, DATATYPE_TOTAL_TEMP);
				// NOW: DATATYPE_TOTAL_TEMP = a[1] - c[1]
			}

			// take snapshot for data type children (known locations)
			takeSnapshotDeep(0, cs, map, classSize);

			// create unknown node for datatype if tracked them (was enabled in config for non-array datatypes)
			if (trackClassUnknown) {
				// Snapshot sum was recomputed, so we've added total from all known locations including clones
				// NOW: cs = a[0] + (c[1] - c[0]) + (k[1] - k[0])

				// Add clone count to temp to figure out the overall datatype total
				addCloneLocationsShallow(cs, DATATYPE_TOTAL_TEMP);
				// NOW: DATATYPE_TOTAL_TEMP = a[1]

				// Figure out our new unknown addition
				DATATYPE_TOTAL_TEMP.subShallow(cs);
				// NOW: DATATYPE_TOTAL_TEMP = a[1] - a[0] - (c[1] - c[0]) - (k[1] - k[0])
				//       = (a[1] - c[1] - k[1]) - (a[0] - c[0] - k[0])  [THESE ARE OUR NEW UNKNOWN FRIENDS!]

				// We can get negative unknown counters, when object was allocated via "new" and its allocation
				// was recorded, but exception has happened before execution had reached Object.<init>,
				// so we clamp unknown counters to positions value
				DATATYPE_TOTAL_TEMP.ensurePositive();

				cs.addToUnknown(DATATYPE_TOTAL_TEMP);
				cs.addShallow(DATATYPE_TOTAL_TEMP); // add unknown totals to overall totals
				DATATYPE_TOTAL_TEMP.clearShallow();
				// NOW: cs = a[1]
			} else
				assert DATATYPE_TOTAL_TEMP.isEmpty(); // otherwise, datatype counters should not have anything in them
		}
		// recompute overall totals
		ss.updateSnapshotSumShallow();
	}

	private static void takeSnapshotShallow(SnapshotShallow ss, IndexMap map, long classSize) {
		long count = map.takeCount(); // SIC! Its long to avoid overflows
		if (map.hasHistogram()) {
			// Array (dynamically tracked sum size with histograms)
			long size = map.takeSize();
			ss.add(count, size);
			for (int i = 0; i < map.getHistogramLength(); i++)
				ss.addHistoCount(i, map.takeHistogramCount(i));
		} else {
			// Regular object (fixed known size)
			ss.add(count, count * classSize);
		}
	}

	/**
	 * Recursively adds snapshot from {@code map} to {@code ss} for a class of a known size {@code classSize}
	 * (which is zero for arrays or when size is not being tracked).
	 */
	// PRE-CONDITION: ss.sortChildrenDeep(SnapshotDeep.COMPARATOR_NAME)
	private static void takeSnapshotDeep(int depth, SnapshotDeep ss, IndexMap map, long classSize) {
		ss.ensureChildrenCapacity(map.getChildrenCount());
		if (map.getChildrenCount() > 0) {
			// process all children
			SnapshotDeepVisitor visitor = SNAPSHOT_DEEP_VISITOR[depth];
			if (visitor == null)
				SNAPSHOT_DEEP_VISITOR[depth] = visitor = new SnapshotDeepVisitor();
			visitor.depth = depth;
			visitor.ss = ss;
			visitor.classSize = classSize;
			map.visitChildren(visitor);
		}
		// update an overall sum for this snapshot
		ss.updateSnapshotSumShallow();
	}

	private static class SnapshotDeepVisitor implements IndexMapVisitor {
		int depth;
		SnapshotDeep ss;
		long classSize;

		public void acceptChild(IndexMap childMap) {
			int loc = childMap.getLocation();
			String name = LOCATIONS.get(loc);
			// Use "findChild" for UNKNOWN_LOC, because UNKNOWN child might get created by "addToUnknown" for other
			// reasons, so it will be appended to the end of the children list and will not be findable by
			// "findChildInSorted" method that is used to find all other children
			SnapshotDeep cs = ss.getOrCreateChildAt(loc == UNKNOWN_LOC ? ss.findChild(name) : ss.findChildInSorted(name), name);
			if (childMap instanceof RootIndexMap && ((RootIndexMap)childMap).isPossiblyEliminatedAllocation()) {
				// copy possibly eliminated allocation attribute
				cs.setPossiblyEliminatedAllocation();
			}
			if (childMap.getChildrenCount() > 0) {
				// if child will have children, then move whatever counters it had while it had no children to UNKNOWN
				if (!cs.hasChildren())
					cs.addToUnknown(cs);
				// and move its shallow snapshot to UNKNOWN
				assert UNKNOWN_TEMP.isEmpty();
				takeSnapshotShallow(UNKNOWN_TEMP, childMap, classSize);
				cs.addToUnknown(UNKNOWN_TEMP);
				UNKNOWN_TEMP.clearShallow();
				// and go recursively into its children
				takeSnapshotDeep(depth + 1, cs, childMap, classSize);
			} else {
				// child has no children of its own -- just take its shallow snapshot
				takeSnapshotShallow(cs, childMap, classSize);
			}
		}
	}

	private static void addCloneLocationsShallow(SnapshotDeep ss, SnapshotShallow cloneTotal) {
		for (int i = 0; i < ss.getUsed(); i++) {
			SnapshotDeep cs = ss.getChild(i);
			if (cs.getName().endsWith(OBJECT_CLONE_SUFFIX))
				cloneTotal.addShallow(cs);
		}
	}

	private static void subCloneLocationsShallow(SnapshotDeep ss, SnapshotShallow cloneTotal) {
		for (int i = 0; i < ss.getUsed(); i++) {
			SnapshotDeep cs = ss.getChild(i);
			if (cs.getName().endsWith(OBJECT_CLONE_SUFFIX))
				cloneTotal.subShallow(cs);
		}
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
