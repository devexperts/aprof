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

import com.devexperts.aprof.util.Log;

/**
 * @author Dmitry Paraschenko
 */
class DetailsConfiguration {
	public static String RESOURCE = "details.config";

	private static final String ANY_METHOD = "*";

	/**
	 * Maps class name to a set of tracked method names.
	 */
	private final Map<String, Set<String>> trackedLocations = new HashMap<String, Set<String>>();

	private final Set<String> remainingClasses = new LinkedHashSet<String>();

	private boolean bootstrapLoaderAnalyzed;

	private ClassLoader lastAnalyzedLoader; // leak at most one class per class loader

	public DetailsConfiguration() {}

	public synchronized void loadFromResource() throws IOException {
		loadFromStream(ClassLoader.getSystemResourceAsStream(RESOURCE));
		for (String str : trackedLocations.keySet())
			remainingClasses.add(str);
	}

	public synchronized void loadFromFile(String fileName) throws IOException {
		if (fileName == null || fileName.trim().length() == 0)
			return;
		loadFromStream(new FileInputStream(fileName));
		for (String str : trackedLocations.keySet())
			remainingClasses.add(str);
	}

	public synchronized void addClassMethods(String[] locations) throws IOException {
		for (String location : locations) {
			int pos = location.lastIndexOf('.');
			if (pos < 0)
				throw new IllegalArgumentException("Location is <class>.<method>");
			String className = location.substring(0, pos);
			String methodName = location.substring(pos + 1);
			getOrCreateClassMethods(className).add(methodName);
		}
	}

	public synchronized boolean isLocationTracked(String locationClass, String locationMethod) {
		Map<String, Set<String>> tracked = trackedLocations;
		Set<String> trackedMethods = tracked.get(locationClass);
		return trackedMethods != null &&
			(trackedMethods.contains(ANY_METHOD) || trackedMethods.contains(locationMethod));
	}

	/**
	 * Analyzes tracked classes in the specified class loader.
	 */
	public synchronized void analyzeTrackedClasses(ClassLoader loader, TransformerAnalyzer analyzer, boolean verbose) {
		if (remainingClasses.isEmpty() || (loader == null ? bootstrapLoaderAnalyzed : loader == lastAnalyzedLoader))
			return;
		List<String> processedClasses = new ArrayList<String>();
		for (String className : remainingClasses) {
			Set<String> methods = trackedLocations.get(className);
			List<String> parents = analyzer.getImmediateClassParents(className, loader);
			if (parents == null)
				continue;
			if (verbose)
				Log.out.println("Resolving tracked class: " + className);
			for (String parent : parents)
				analyzeClassRec(parent, loader, analyzer, methods);
			processedClasses.add(className);
		}
		remainingClasses.removeAll(processedClasses);
		if (loader == null)
			bootstrapLoaderAnalyzed = true;
		else
			lastAnalyzedLoader = loader;
	}

	private void analyzeClassRec(String className, ClassLoader loader, TransformerAnalyzer analyzer, Set<String> newMethods) {
		Set<String> methods = trackedLocations.get(className);
		if (methods == null)
			trackedLocations.put(className, methods = new HashSet<String>());
		methods.addAll(newMethods);
		List<String> parents = analyzer.getImmediateClassParents(className, loader);
		if (parents != null)
			for (String parent : parents)
				analyzeClassRec(parent, loader, analyzer, newMethods);
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
