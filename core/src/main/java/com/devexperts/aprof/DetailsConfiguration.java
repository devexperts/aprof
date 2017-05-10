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

import java.io.*;
import java.util.*;

/**
 * This class keeps details configuration as read from file and configuration options.
 * It is not modified during run-time. Class hierarchy is analyzed and cached by transformer.
 */
class DetailsConfiguration {
	public static String RESOURCE = "details.config";

	private static final String ANY_METHOD = "*";

	/**
	 * Maps class name to a set of tracked method names.
	 */
	private final Map<String, Set<String>> trackedLocations = new LinkedHashMap<String, Set<String>>();

	public DetailsConfiguration() {}

	public void loadFromResource() throws IOException {
		loadFromStream(ClassLoader.getSystemResourceAsStream(RESOURCE));
	}

	public void loadFromFile(String fileName) throws IOException {
		if (fileName == null || fileName.trim().length() == 0)
			return;
		loadFromStream(new FileInputStream(fileName));
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

	public Set<String> getTrackedClasses() {
		return trackedLocations.keySet();
	}

	public boolean isMethodTracked(String className, String methodName) {
		Set<String> trackedMethods = trackedLocations.get(className);
		return trackedMethods != null &&
			(trackedMethods.contains(ANY_METHOD) || trackedMethods.contains(methodName));
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
				boolean indentedLine = Character.isWhitespace(line.charAt(0));
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
