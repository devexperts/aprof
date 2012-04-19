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

import java.io.*;
import java.util.*;

/**
 * @author Dmitry Paraschenko
 */
class DetailsConfiguration {
	public static String RESOURCE = "details.config";

	private static final String ANY_METHOD = "*";
	private static final Set<String> ALL_METHODS = Collections.singleton(ANY_METHOD);

	/** Class name --> set of method names. */
	private Map<String, Set<String>> trackedLocations = new HashMap<String, Set<String>>();

	private HashSet<String> remainingClasses = new HashSet<String>();
	private boolean reloadTrackedClasses;

	public DetailsConfiguration() {
	}

	public void loadFromResource() throws IOException {
		loadFromStream(ClassLoader.getSystemResourceAsStream(RESOURCE));
		for (String str : trackedLocations.keySet()) {
			remainingClasses.add(str);
		}
	}

	public void loadFromFile(String fileName) throws IOException {
		if (fileName == null || fileName.trim().isEmpty()) {
			return;
		}
		loadFromStream(new FileInputStream(fileName));
		for (String str : trackedLocations.keySet()) {
			remainingClasses.add(str);
		}
	}

	public void addClasses(String[] classNames) throws IOException {
		for (String cname : classNames) {
			trackedLocations.put(cname, ALL_METHODS);
		}
	}

	public void reloadTrackedClasses() {
		reloadTrackedClasses = true;
	}

	public boolean isLocationTracked(String location) {
		int pos = location.indexOf('(');
		if (pos >= 0) {
			location = location.substring(0, pos);
		}
		pos = location.lastIndexOf('.');
		String className = location.substring(0, pos);
		String methodName = location.substring(pos + 1);
		Map<String, Set<String>> tracked = getTrackedMethods();
		Set<String> trackedMethods = tracked.get(className);
		if (trackedMethods == null) {
			return false;
		}
		if (trackedMethods.isEmpty() || trackedMethods.contains(ANY_METHOD)) {
			return true;
		}
		return trackedMethods.contains(methodName);
	}

	private Map<String, Set<String>> getTrackedMethods() {
		if (reloadTrackedClasses) {
			reloadTrackedClasses = false;
			ArrayList<String> processedClasses = null;
			for (String cname : remainingClasses) {
				try {
					Class clazz = Class.forName(cname);
					Set<String> methods = trackedLocations.get(cname);
					if (methods.contains(ANY_METHOD)) {
						methods = ALL_METHODS;
					}
					while (clazz != null && clazz != Object.class) {
						addInterfaces(clazz, methods);
						clazz = clazz.getSuperclass();
					}
					if (processedClasses == null) {
						processedClasses = new ArrayList<String>();
					}
					processedClasses.add(cname);
				} catch (ClassNotFoundException e) {
					// do nothing
				}
			}
			if (processedClasses != null) {
				remainingClasses.removeAll(processedClasses);
			}
		}
		return trackedLocations;
	}

	private void addInterfaces(Class clazz, Set<String> classMethods) {
		Set<String> methods = trackedLocations.get(clazz.getCanonicalName());
		if (methods == null) {
			methods = new HashSet<String>();
			String className = clazz.getCanonicalName();
			className = new String(className.toCharArray());
			trackedLocations.put(className, methods);
		}
		methods.addAll(classMethods);
		Class[] interfaces = clazz.getInterfaces();
		if (interfaces != null) {
			for (Class interfacce : interfaces) {
				addInterfaces(interfacce, classMethods);
			}
		}
	}

	private void loadFromStream(InputStream stream) throws IOException {
		BufferedReader in = null;
		try {
			in = new BufferedReader(new InputStreamReader(stream));
			Set<String> classMethods = null;
			while (true) {
				String line = in.readLine();
				if (line == null) {
					break;
				}
				line = line.trim();
				if (line.startsWith(Configuration.COMMENT)) {
					continue;
				}
				if (line.length() == 0) {
					classMethods = null;
					continue;
				}
				if (classMethods == null) {
					classMethods = trackedLocations.get(line);
					if (classMethods == null) {
						classMethods = new HashSet<String>();
						trackedLocations.put(line, classMethods);
					}
				} else {
					classMethods.add(line);
				}
			}
		} finally {
			if (in != null) {
				in.close();
			}
		}
	}
}
