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
import java.lang.instrument.*;
import java.lang.reflect.Constructor;
import java.net.URL;
import java.util.*;

import com.devexperts.aprof.dump.*;
import com.devexperts.aprof.hotspot.CompileLogWatcher;
import com.devexperts.aprof.util.*;

/**
 * @author Roman Elizarov
 */
public class AProfAgent {
	private static final String TRANSFORMER_CLASS = "com.devexperts.aprof.transformer.AProfTransformer";

	private static final List<String> CLASSPATH_JARS = Arrays.asList(
			"transformer.jar",
			"asm.jar",
			"asm-analysis.jar",
			"asm-commons.jar",
			"asm-tree.jar",
			"asm-util.jar"
	);

	private static InnerJarClassLoader classLoader;
	private static AProfAgent INSTANCE;

	private synchronized static InnerJarClassLoader getClassLoader() throws IOException, ClassNotFoundException {
		if (classLoader == null) {
			List<URL> urls = new ArrayList<URL>();
			for (String jar : CLASSPATH_JARS) {
				URL url = Thread.currentThread().getContextClassLoader().getResource(jar);
				urls.add(url);
			}
			classLoader = new InnerJarClassLoader(urls);
		}
		return classLoader;
	}

	public static void premain(String agentArgs, Instrumentation inst) throws Exception {
		getClassLoader();
		Configuration config = new Configuration(agentArgs);
		File configFile = new File(config.getConfigFile());
		config = new Configuration(configFile, agentArgs);
		Log.initFile(config.getLogFile());
		INSTANCE = new AProfAgent(config, inst);
		INSTANCE.go();
	}

	public static AProfAgent getInstance() {
		return INSTANCE;
	}

// -------------------- instance members --------------------

	private final long start;
	private final Configuration config;
	private final Instrumentation inst;
	private final Dumper dumper;

	public AProfAgent(Configuration config, Instrumentation inst) {
		this.start = System.currentTimeMillis();
		this.config = config;
		this.inst = inst;
		this.dumper = new Dumper(config, start);
	}

	public Configuration getConfig() {
		return config;
	}

	public Dumper getDumper() {
		return dumper;
	}

	@SuppressWarnings("unchecked")
	public void go() throws Exception {
		StringBuilder sb = new StringBuilder();
		logClearSbAlways(sb.append("Loading ").append(Version.full()).append("..."));

		if (!config.showNotes(Log.out, false))
			throw new IllegalArgumentException("Invalid aprof configuration arguments.");

		if (config.isCheckEliminateAllocation() || config.isVerboseEliminateAllocation()) {
			CompileLogWatcher thread = new CompileLogWatcher(config);
			thread.start();
		}

		InnerJarClassLoader classLoader = getClassLoader();

		AProfSizeUtil.init(inst);
		AProfRegistry.init(config);

		Class<ClassFileTransformer> transformerClass = (Class<ClassFileTransformer>)classLoader.loadClass(TRANSFORMER_CLASS);
		Constructor<ClassFileTransformer> transformerConstructor = transformerClass.getConstructor(Configuration.class);
		ClassFileTransformer transformer = transformerConstructor.newInstance(config);

		// redefine all classes loader so far
		redefine(transformer);

		inst.addTransformer(transformer);
		log("Done redefining, transformer installed");

		// dumping
		log("Making first dump...");
		dumper.makeDump(false);

		DumpPeriodicThread dpt = null;
		if (config.getTime() > 0) {
			log("Starting dumper thread...");
			dpt = new DumpPeriodicThread(dumper, config.getTime());
			dpt.start();
		}

		long finish = System.currentTimeMillis();
		long transformTime = AProfRegistry.getTime();
		log("Attaching shutdown hook...");
		Runtime.getRuntime().addShutdownHook(new DumpShutdownThread(dumper, finish, transformTime, dpt));

		// listening on port
		if (config.getPort() > 0) {
			logClearSb(sb.append("Listening on port ").append(config.getPort()));
			Thread t = new ConnectionListenerThread(config.getPort(), dumper);
			t.start();
		}

		// done
		logClearSbAlways(sb.append("Loaded in ").append(finish - start).append(" ms with ").append(transformTime).
			append(" ms in transformer (").append(finish - start - transformTime).append(" ms other)"));
	}

	private void redefine(ClassFileTransformer transformer)
		throws IllegalClassFormatException, ClassNotFoundException, UnmodifiableClassException
	{
		StringBuilder sb = new StringBuilder();
		ArrayList<Class> classes = new ArrayList<Class>();
		HashSet<Class> done = new HashSet<Class>();
		FastByteBuffer buf = new FastByteBuffer();
		for (int pass = 1;; pass++) {
			classes.addAll(Arrays.asList(inst.getAllLoadedClasses()));
			List<ClassDefinition> cdl = new ArrayList<ClassDefinition>(classes.size());
			logClearSb(sb.append("Redefining classes pass #").append(pass).append("..."));
			for (Class clazz : classes) {
				if (clazz.isArray())
					continue;
				if (!done.add(clazz))
					continue;
				String name = clazz.getName().replace('.', '/');
				InputStream is = clazz.getResourceAsStream("/" + name + ".class");
				buf.clear();
				if (is != null)
					try {
						try {
							buf.readFrom(is);
						} finally {
							is.close();
						}
					} catch (IOException e) {
						logClearSb(sb.append("Failed to read class resource: ").append(name).append(' ').append(e));
					}
				if (buf.isEmpty()) {
					logClearSb(sb.append("Cannot read class resource: ").append(name));
					continue;
				}
				byte[] result = transformer.transform(
					clazz.getClassLoader(), name, clazz, clazz.getProtectionDomain(), buf.getBytes());
				if (result != null)
					cdl.add(new ClassDefinition(clazz, result));
			}
			classes.clear();
			if (cdl.isEmpty())
				break; // all classes were redefined
			logClearSb(sb.append("Redefining classes pass #").append(pass).append("..."));

			if (config.isVerboseRedefinition()) {
				for (ClassDefinition cd : cdl) {
					String name = cd.getDefinitionClass().getName();
					logClearSb(sb.append("Redefining class ").append(name));
					try {
						inst.redefineClasses(new ClassDefinition[] {cd});
					} catch (Exception e) {
						logClearSb(sb.append("Failed to redefine class ").append(name).append(": ").append(e));
					}
				}
			} else {
				inst.redefineClasses(cdl.toArray(new ClassDefinition[cdl.size()]));
			}
		}
	}

	private void log(Object o) {
		if (config.isVerbose())
			Log.out.println(o);
	}

	private void logClearSb(StringBuilder sb) {
		log(sb);
		sb.setLength(0);
	}

	private void logClearSbAlways(StringBuilder sb) {
		Log.out.println(sb);
		sb.setLength(0);
	}

}
