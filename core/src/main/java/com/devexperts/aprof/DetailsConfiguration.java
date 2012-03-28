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
    private Map<String, Set<String>> tracked_locations = new HashMap<String, Set<String>>();

    private HashSet<String> remaining_classes = new HashSet<String>();
    private boolean reload_tracked_classes;

    public DetailsConfiguration() {
    }

    public void loadFromResource() throws IOException {
        loadFromStream(ClassLoader.getSystemResourceAsStream(RESOURCE));
        for (String str : tracked_locations.keySet()) {
            remaining_classes.add(str);
        }
    }

    public void loadFromFile(String file_name) throws IOException {
        if (file_name == null || file_name.trim().isEmpty()) {
            return;
        }
        loadFromStream(new FileInputStream(file_name));
        for (String str : tracked_locations.keySet()) {
            remaining_classes.add(str);
        }
    }

    public void addClasses(String[] class_names) throws IOException {
        for (String cname : class_names) {
            tracked_locations.put(cname, ALL_METHODS);
        }
    }

    public void reloadTrackedClasses() {
        reload_tracked_classes = true;
    }

    public boolean isLocationTracked(String location) {
        int pos = location.indexOf('(');
        if (pos >= 0) {
            location = location.substring(0, pos);
        }
        pos = location.lastIndexOf('.');
        String class_name = location.substring(0, pos);
        String method_name = location.substring(pos + 1);
        Map<String, Set<String>> tracked = getTrackedMethods();
        Set<String> tracked_methods = tracked.get(class_name);
        if (tracked_methods == null) {
            return false;
        }
        if (tracked_methods.isEmpty() || tracked_methods.contains(ANY_METHOD)) {
            return true;
        }
        return tracked_methods.contains(method_name);
    }

    private Map<String, Set<String>> getTrackedMethods() {
        if (reload_tracked_classes) {
            reload_tracked_classes = false;
            ArrayList<String> processed_classes = null;
            for (String cname : remaining_classes) {
                try {
                    Class clazz = Class.forName(cname);
                    Set<String> methods = tracked_locations.get(cname);
                    if (methods.contains(ANY_METHOD)) {
                        methods = ALL_METHODS;
                    }
                    while (clazz != null && clazz != Object.class) {
                        addInterfaces(clazz, methods);
                        clazz = clazz.getSuperclass();
                    }
                    if (processed_classes == null) {
                        processed_classes = new ArrayList<String>();
                    }
                    processed_classes.add(cname);
                } catch (ClassNotFoundException e) {
                    // do nothing
                }
            }
            if (processed_classes != null) {
                remaining_classes.removeAll(processed_classes);
            }
        }
        return tracked_locations;
    }

    private void addInterfaces(Class clazz, Set<String> class_methods) {
        Set<String> methods = tracked_locations.get(clazz.getCanonicalName());
        if (methods == null) {
            methods = new HashSet<String>();
            String class_name = clazz.getCanonicalName();
            class_name = new String(class_name.toCharArray());
            tracked_locations.put(class_name, methods);
        }
        methods.addAll(class_methods);
        Class[] interfaces = clazz.getInterfaces();
        if (interfaces != null) {
            for (Class interfacce : interfaces) {
                addInterfaces(interfacce, class_methods);
            }
        }
    }

    private void loadFromStream(InputStream stream) throws IOException {
        BufferedReader in = null;
        try {
            in = new BufferedReader(new InputStreamReader(stream));
            Set<String> class_methods = null;
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
                    class_methods = null;
                    continue;
                }
                if (class_methods == null) {
                    class_methods = tracked_locations.get(line);
                    if (class_methods == null) {
                        class_methods = new HashSet<String>();
                        tracked_locations.put(line, class_methods);
                    }
                } else {
                    class_methods.add(line);
                }
            }
        } finally {
            if (in != null) {
                in.close();
            }
        }
    }
}
