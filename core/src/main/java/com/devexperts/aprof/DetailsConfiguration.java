/*
 * Aprof - Java Memory Allocation Profiler
 * Copyright (C) 2002-2014  Devexperts LLC
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

import java.io.*;
import java.util.*;

/**
 * @author Dmitry Paraschenko
 */
class DetailsConfiguration {
	public static String RESOURCE = "details.config";

	private static final String ANY_METHOD = "*";

	/** Class name --> set of method names. */
	private Map<String, Set<String>> trackedLocations = new HashMap<String, Set<String>>();

	private HashSet<String> remainingClasses = new HashSet<String>();
	private boolean reloadTrackedClasses;

	public DetailsConfiguration() {}

	public void loadFromResource() throws IOException {
		loadFromStream(ClassLoader.getSystemResourceAsStream(RESOURCE));
		for (String str : trackedLocations.keySet())
			remainingClasses.add(str);
	}

	public void loadFromFile(String fileName) throws IOException {
		if (fileName == null || fileName.trim().length() == 0)
			return;
		loadFromStream(new FileInputStream(fileName));
		for (String str : trackedLocations.keySet())
			remainingClasses.add(str);
	}

	public void addClassMethods(String[] locations) throws IOException {
		for (String location : locations) {
			int pos = location.lastIndexOf('.');
			if (pos < 0)
				throw new IllegalArgumentException("Location is <class>.<method>");
			String className = location.substring(0, pos);
			String methodName = location.substring(pos + 1);
			getOrCreateClassMethods(className).add(methodName);
		}
	}

	public void reloadTrackedClasses() {
		reloadTrackedClasses = true;
	}

	public boolean isLocationTracked(String locationClass, String locationMethod) {
		Map<String, Set<String>> tracked = getTrackedMethods();
		Set<String> trackedMethods = tracked.get(locationClass);
		return trackedMethods != null &&
			(trackedMethods.isEmpty() || trackedMethods.contains(ANY_METHOD) || trackedMethods.contains(locationMethod));
	}

	private Map<String, Set<String>> getTrackedMethods() {
		if (reloadTrackedClasses) {
			reloadTrackedClasses = false;
			ArrayList<String> processedClasses = null;
			for (String className : remainingClasses) {
				try {
					Class clazz = Class.forName(className);
					Set<String> methods = trackedLocations.get(className);
					while (clazz != null && clazz != Object.class) {
						addInterfaces(clazz, methods);
						clazz = clazz.getSuperclass();
					}
					if (processedClasses == null)
						processedClasses = new ArrayList<String>();
					processedClasses.add(className);
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
		Set<String> methods = trackedLocations.get(clazz.getName());
		if (methods == null)
			trackedLocations.put(clazz.getName(), methods = new HashSet<String>());
		methods.addAll(classMethods);
		Class[] interfaces = clazz.getInterfaces();
		if (interfaces != null)
			for (Class anInterface : interfaces)
				addInterfaces(anInterface, classMethods);
	}

	private void loadFromStream(InputStream stream) throws IOException {
		BufferedReader in = new BufferedReader(new InputStreamReader(stream));
		try {
			Set<String> classMethods = null;
			for (int lineNo = 1;; lineNo++) {
				String line = in.readLine();
				if (line == null)
					break;
				if (line.length() == 0)
					continue;
				boolean indentedLine = Character.isSpaceChar(line.charAt(0));
				line = line.trim();
				if (line.length() == 0 || line.startsWith(Configuration.COMMENT))
					continue;
				if (indentedLine) {
					if (classMethods == null)
						throw new IOException(String.format(
							"Line %d: Indented line with method name shall follow a line with class name", lineNo));
					classMethods.add(line);
				} else {
					// non-indented line with a class-name
					classMethods = getOrCreateClassMethods(line);
				}
			}
		} finally {
			in.close();
		}
	}

	private Set<String> getOrCreateClassMethods(String className) {
		Set<String> classMethods = trackedLocations.get(className);
		if (classMethods == null)
			trackedLocations.put(className, classMethods = new HashSet<String>());
		return classMethods;
	}
}
