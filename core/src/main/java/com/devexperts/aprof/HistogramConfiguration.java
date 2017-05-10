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
 * @author Dmitry Paraschenko
 */
class HistogramConfiguration {
	private static final String ANY_CLASS = "*";

	private int[] commonHistogram;
	private Map<String, int[]> classHistograms;

	public HistogramConfiguration(int[] commonHistogram, String histogramFileName) throws IOException {
		this.commonHistogram = commonHistogram;
		this.classHistograms = new HashMap<String, int[]>();
		loadFromFile(histogramFileName);
	}

	public int[] getHistogram(String className) {
		int[] histogram = classHistograms.get(className);
		return histogram != null ? histogram : commonHistogram;
	}

	public int getMaxHistogramLength() {
		int result = commonHistogram.length;
		for (int[] histogram : classHistograms.values())
			result = Math.max(result, histogram.length);
		return result;
	}

	private void loadFromFile(String fileName) throws IOException {
		if (fileName == null || fileName.trim().length() == 0)
			return;
		BufferedReader in = new BufferedReader(new FileReader(fileName));
		try {
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
					continue;
				}
				String class_name = ANY_CLASS;
				int pos = line.indexOf('=');
				if (pos > 0) {
					class_name = line.substring(0, pos);
				}
				line = line.substring(pos + 1);
				StringTokenizer st = new StringTokenizer(line, ",");
				ArrayList<Integer> list = new ArrayList<Integer>();
				while (st.hasMoreTokens()) {
					int size = Integer.parseInt(st.nextToken().trim());
					if (!list.contains(size)) {
						list.add(size);
					}
				}
				int[] histogram = new int[list.size()];
				for (int i = 0; i < histogram.length; i++) {
					histogram[i] = list.get(i);
				}
				Arrays.sort(histogram);
				if (ANY_CLASS.equals(class_name)) {
					commonHistogram = histogram;
				} else {
					classHistograms.put(class_name, histogram);
				}
			}
		} finally {
			in.close();
		}
	}
}
