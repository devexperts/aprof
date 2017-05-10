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
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;

/**
 * @author Roman Elizarov
 */
public class Configuration {
	public static final LinkedHashMap<String, Prop> PROPS = createProps();

	public static final String COMMENT = "#";
	private static final int SEC = 1000;
	private static final int MIN = 60 * SEC;

	private static final String XX_UNLOCK_DIAGNOSTIC_VM_OPTIONS = "-XX:+UnlockDiagnosticVMOptions";
	private static final String XX_LOG_COMPILATION = "-XX:+LogCompilation";

	@Description("Configuration file.")
	private String config_file = "aprof.config";

	@Description("File location for Aprof dump, empty to work without file. Sharps (##) are used to specify file number.")
	private String file = "aprof.txt";

	@Description("Whether Aprof dump shall be appended to the file instead of rewriting it.")
	private boolean file_append = false;

	@Description(value="Number of files to be used to write Aprof dumps. Zero means no limit on file number.")
	private int filecount = 0;

	@Description("Threshold for entries in Aprof dump in (%).")
	private double threshold = 0.01;

	@Description("Expand shown classes (even below threshold) up to specified level (0 -- do not expand).")
	private int level = 2;

	@TimeIntervalProp
	@Description("Time period to write Aprof dump file.")
	private long time = MIN;

	@Description("Be verbose and log every class transformation.")
	private boolean verbose = false;

	@Description("Be verbose about class redefinitions.")
	private boolean verbose_redefinition = false;

	@Description("Be verbose about available tracked classes.")
	private boolean verbose_tracked = false;

	@Description("Be verbose about allocations eliminated by HotSpot during compilation.")
	private boolean verbose_eliminate_allocation = false;

	@Description("Check allocations eliminated by HotSpot during compilation and report them separately.")
	private boolean check_eliminate_allocation = false;

	@Description("Write aprof log to file")
	private String log_file = "";

	@Description("Dump aprof-instrumented class into a specified directory.")
	private String dump_classes_dir = "";

	@Description("Skip debug information (line numbers and local variables) during class transformation.")
	private boolean skipdebug = false;

	@Description("Omit stack frames during class transformation for 1.6+ classes.")
	private boolean noframes = false;

	@Description("Instrument allocations via reflection (Array.newInstance, Object.clone).")
	private boolean reflect = true;

	@Description("Instrument java.lang.Object constructor to track object allocations from unknown locations. Prevents stack-allocation of objects.")
	private boolean unknown = false;

	@Description("Keep track of allocated object sizes.")
	private boolean size = true;

	@Description("Comma-separated list of classes that shall not be transformed.")
	private String[] exclude = new String[0];

	@Description("Comma-separated list of locations that shall include call signature.")
	private String[] signature = new String[] { "java.lang.String.<init>" };

	@Description("Comma-separated list of prefixes of class names that shall be aggregated (use for generated classes).")
	private String[] aggregate = new String[] {
			"sun.reflect.GeneratedSerializationConstructorAccessor",
			"sun.reflect.GeneratedConstructorAccessor",
			"sun.reflect.GeneratedMethodAccessor"
	};

	@Description("Comma-separated list of location names which shall be additionally tracked (use class.method or class.* for all methods).")
	private String[] track = new String[] {};

	@Description("File name for tracked locations configuration.")
	private String track_file = "";

	@Description("Whether track.file replaces default configuration.")
	private boolean track_file_replace = false;

	@Description("Comma-separated list of array lengths which shall be shown in histograms.")
	private int[] histogram = new int[0];

	@Description("File name for histogram configuration.")
	private String histogram_file = "";

	@Description("Port to listen on.")
	private int port = 0;

	private DetailsConfiguration detailsConfig;
	private HistogramConfiguration histogramConfig;

	public Configuration() throws IOException {
		seal();
	}

	public Configuration(String string) throws IOException {
		applyString(string);
		seal();
	}

	public Configuration(File file, String string) throws IOException {
		if (file.exists()) {
			BufferedReader in = new BufferedReader(new FileReader(file));
			while (true) {
				String line = in.readLine();
				if (line == null) {
					break;
				}
				line = line.trim();
				if (line.startsWith(COMMENT)) {
					continue;
				}
				applyString(line);
			}
		}
		applyString(string);
		seal();
	}

	private void seal() throws IOException {
		detailsConfig = new DetailsConfiguration();
		if (!track_file_replace)
			detailsConfig.loadFromResource();
		detailsConfig.loadFromFile(track_file);
		detailsConfig.addClassMethods(track);
		histogramConfig = new HistogramConfiguration(histogram, histogram_file);
	}

	public String getConfigFile() {
		return config_file;
	}

	public String getFile() {
		return file;
	}

	public boolean isFileAppend() {
		return file_append;
	}

	public int getFilecount() {
		return filecount;
	}

	public double getThreshold() {
		return threshold;
	}

	public int getLevel() {
		return level;
	}

	public long getTime() {
		return time;
	}

	public boolean isVerbose() {
		return verbose;
	}

	public boolean isVerboseRedefinition() {
		return verbose_redefinition;
	}

	public boolean isVerboseTracked() {
		return verbose_tracked;
	}

	public boolean isVerboseEliminateAllocation() {
		return verbose_eliminate_allocation;
	}

	public boolean isCheckEliminateAllocation() {
		return check_eliminate_allocation;
	}

	public String getLogFile() {
		return log_file;
	}

	public String getDumpClassesDir() {
		return dump_classes_dir;
	}

	public boolean isSkipDebug() {
		return skipdebug;
	}

	public boolean isNoFrames() {
		return noframes;
	}

	public boolean isReflect() {
		return reflect;
	}

	public boolean isUnknown() {
		return unknown;
	}

	public boolean isSize() {
		return size;
	}

	public String[] getExcludedClasses() {
		return exclude;
	}

	public boolean isSignatureLocation(CharSequence location) {
		for (String s : signature) {
			if (s.contentEquals(location))
				return true;
		}
		return false;
	}

	public String[] getAggregatedClasses() {
		return aggregate;
	}

	public int[] getHistogram(String className) {
		return histogramConfig.getHistogram(className);
	}

	public int getMaxHistogramLength() {
		return histogramConfig.getMaxHistogramLength();
	}

	public int getPort() {
		return port;
	}

	public Set<String> getTrackedClasses() {
		return detailsConfig.getTrackedClasses();
	}

	public boolean isMethodTracked(String className, String methodName) {
		return detailsConfig.isMethodTracked(className, methodName);
	}

	public void applyString(String string) {
		if (string == null)
			return;
		StringTokenizer st = new StringTokenizer(string, ":");
		while (st.hasMoreTokens()) {
			String t = st.nextToken().trim();
			int i = t.indexOf('=');
			String name;
			String value;
			if (i >= 0) {
				name = t.substring(0, i).toLowerCase(Locale.US);
				value = t.substring(i + 1);
			} else if (t.startsWith("+")) {
				name = t.substring(1);
				value = "true";
			} else if (t.startsWith("-")) {
				name = t.substring(1);
				value = "false";
			} else if (t.endsWith("+")) {
				name = t.substring(0, t.length() - 1);
				value = "true";
			} else if (t.endsWith("-")) {
				name = t.substring(0, t.length() - 1);
				value = "false";
			} else {
				throw new IllegalArgumentException("Missing '=' for argument: " + t);
			}
			name = name.trim();
			value = value.trim();
			Prop p = PROPS.get(name);
			if (p == null)
				throw new IllegalArgumentException("Unknown argument: " + name);
			try {
				p.field.set(this, parseValue(value, p));
			} catch (IllegalAccessException e) {
				throw new IllegalArgumentException(e);
			}
		}
		filecount = Math.max(0, filecount);
		Arrays.sort(histogram);
	}

	public boolean showNotes(PrintWriter out, boolean all) {
		boolean ok = true;
		if (all || histogram.length > 0 && !size) {
			out.println("Note: 'histogram' option does not work without 'size'.");
			ok = false;
		}
		if (all || verbose_redefinition && !verbose) {
			out.println("Note: 'verbose.redefinition' does not work without 'verbose'.");
			ok = false;
		}
		if (all || (verbose_eliminate_allocation || check_eliminate_allocation) && !isCompileLogEnabled()) {
			out.println("Note: 'check.eliminate.allocation' and 'verbose.eliminate.allocation' requires running JVM with " +
				"'" + XX_UNLOCK_DIAGNOSTIC_VM_OPTIONS + "' and '" + XX_LOG_COMPILATION + "' options.");
			ok = false;
		}
		return ok;
	}

	private boolean isCompileLogEnabled() {
		List<String> args = ManagementFactory.getRuntimeMXBean().getInputArguments();
		return args.contains(XX_UNLOCK_DIAGNOSTIC_VM_OPTIONS) && args.contains(XX_LOG_COMPILATION);
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		for (Prop p: PROPS.values()) {
			if (sb.length() > 0)
				sb.append(':');
			if (p.field.getType() == boolean.class) {
				sb.append((Boolean)get(p) ? "+" : "-");
				sb.append(p.name);
			} else {
				sb.append(p.name);
				sb.append('=');
				sb.append(getString(p));
			}
		}
		return sb.toString();
	}

	private Object get(Prop p) {
		try {
			return p.field.get(this);
		} catch (IllegalAccessException e) {
			throw new IllegalArgumentException(e);
		}
	}

	public String getString(Prop p) {
		Object value = get(p);
		return formatValue(value, p);
	}

	private static String formatValue(Object value, Prop p) {
		if (value.getClass().isArray()) {
			StringBuilder sb = new StringBuilder();
			int n = Array.getLength(value);
			for (int i = 0; i < n; i++) {
				if (i > 0)
					sb.append(",");
				sb.append(formatValue(Array.get(value, i), p));
			}
			return sb.toString();
		} else if (p.field.isAnnotationPresent(TimeIntervalProp.class)) {
			return t2s((Long)value);
		}
		return String.valueOf(value);
	}

	private static String t2s(long time) {
		if (time % MIN == 0)
			return (time / MIN) + "m";
		return ((double)time / MIN) + "m";
	}

	private static Object parseValue(String value, Prop p) {
		return parseValue(value, p, p.field.getType());
	}

	private static Object parseValue(String value, Prop p, Class type) {
		Object v = value;
		if (type.isArray()) {
			StringTokenizer st = new StringTokenizer(value, ",");
			Class ctype = type.getComponentType();
			v = Array.newInstance(ctype, st.countTokens());
			for (int i = 0; st.hasMoreTokens(); i++)
				Array.set(v, i, parseValue(st.nextToken(), p, ctype));
		} else if (type == boolean.class) {
			v = Boolean.valueOf(value);
		} else if (type == double.class) {
			v = Double.valueOf(value);
		} else if (type == int.class) {
			v = Integer.valueOf(value);
		} else if (p.field.isAnnotationPresent(TimeIntervalProp.class)) {
			if (value.endsWith("s")) {
				v = (long)(Double.parseDouble(value.substring(0, value.length() - 1)) * SEC);
			} else if (value.endsWith("m")) {
				v = (long)(Double.parseDouble(value.substring(0, value.length() - 1)) * MIN);
			} else {
				v = Long.valueOf(value);
			}
		}
		return v;
	}

	private static LinkedHashMap<String, Prop> createProps() {
		Field[] fs = Configuration.class.getDeclaredFields();
		LinkedHashMap<String, Prop> res = new LinkedHashMap<String, Prop>();
		for (Field f: fs)
			if (!Modifier.isStatic(f.getModifiers())) {
				Description annotation = f.getAnnotation(Description.class);
				if (annotation != null) {
					String name = f.getName().replace('_', '.');
					res.put(name, new Prop(name, annotation.value(), f));
				}
			}
		return res;
	}

	@Retention(RetentionPolicy.RUNTIME)
	private static @interface Description {
		String value();
	}

	@Retention(RetentionPolicy.RUNTIME)
	private static @interface TimeIntervalProp {}

	public static class Prop {
		private final String name;
		private final String description;
		private final Field field;

		private Prop(String name, String description, Field field) {
			this.name = name;
			this.description = description;
			this.field = field;
		}

		public String getName() {
			return name;
		}

		public String getDescription() {
			return description;
		}
	}
}
