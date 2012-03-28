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
class HistogramConfiguration {
    private static final String ANY_CLASS = "*";

    private int[] common_histogram;
    private Map<String, int[]> class_histograms;

    public HistogramConfiguration(int[] common_histogram, String histogram_file) throws IOException {
        this.common_histogram = common_histogram;
        this.class_histograms = new HashMap<String, int[]>();
        loadFromFile(histogram_file);
    }

    public int[] getHistogram(String class_name) {
        int[] histogram = class_histograms.get(class_name);
        return histogram != null ? histogram : common_histogram;
    }

    private void loadFromFile(String file_name) throws IOException {
        if (file_name == null || file_name.trim().isEmpty()) {
            return;
        }
        BufferedReader in = null;
        try {
            in = new BufferedReader(new FileReader(file_name));
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
                    common_histogram = histogram;
                } else {
                    class_histograms.put(class_name, histogram);
                }
            }
        } finally {
            if (in != null) {
                in.close();
            }
        }
    }
}
