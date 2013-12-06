/*
 * Aprof - Java Memory Allocation Profiler
 * Copyright (C) 2002-2013  Devexperts LLC
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

import com.devexperts.aprof.dump.ConnectionListenerThread;
import com.devexperts.aprof.dump.DumpPeriodicThread;
import com.devexperts.aprof.dump.DumpShutdownThread;
import com.devexperts.aprof.dump.Dumper;
import com.devexperts.aprof.util.InnerJarClassLoader;
import com.devexperts.aprof.util.Log;

import java.io.*;
import java.lang.instrument.ClassDefinition;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Constructor;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

/**
 * @author Roman Elizarov
 */
public class AProfAgent {
	private static final String TRANSFORMER_CLASS = "com.devexperts.aprof.transformer.AProfTransformer";
	private static final String RESOLVER_CLASS = "com.devexperts.aprof.transformer.ClassNameResolverImpl";

	private static final List<String> CLASSPATH_JARS = Arrays.asList(
			"transformer.jar",
			"asm.jar",
			"asm-analysis.jar",
			"asm-commons.jar",
			"asm-tree.jar",
			"asm-util.jar"
	);

	private static InnerJarClassLoader classLoader;

	private synchronized static InnerJarClassLoader getClassLoader() throws IOException, ClassNotFoundException {
		if (classLoader == null) {
			List<URL> urls = new ArrayList<URL>();
			for (String jar : CLASSPATH_JARS) {
				URL url = Thread.currentThread().getContextClassLoader().getResource(jar);
				urls.add(url);
			}
			classLoader = new InnerJarClassLoader(urls);
			classLoader.forceLoadAllClasses();
		}
		return classLoader;
	}

	public static void premain(String agentArgs, Instrumentation inst) throws Exception {
		getClassLoader();
		Configuration config = new Configuration(agentArgs);
		File configFile = new File(config.getConfigFile());
		config = new Configuration(configFile, agentArgs);
		new AProfAgent(config, inst).go();
	}

	private final Configuration config;
	private final Instrumentation inst;

	public AProfAgent(Configuration config, Instrumentation inst) {
		this.config = config;
		this.inst = inst;
	}

	public void go() throws Exception {
		long start = System.currentTimeMillis();
		StringBuilder sb = new StringBuilder();
		sb.append("Loading ").append(Version.full()).append("...");
		Log.out.println(sb);
		config.showNotes(Log.out, false);

		InnerJarClassLoader classLoader = getClassLoader();

		Class<ClassNameResolver> resolverClass = (Class<ClassNameResolver>)classLoader.loadClass(RESOLVER_CLASS);

		ArraySizeHelper.init(inst);
		AProfRegistry.init(config, resolverClass.newInstance());

		Class<ClassFileTransformer> transformerClass = (Class<ClassFileTransformer>)classLoader.loadClass(TRANSFORMER_CLASS);
		Constructor<ClassFileTransformer> transformerConstructor = transformerClass.getConstructor(Configuration.class);
		ClassFileTransformer transformer = transformerConstructor.newInstance(config);

		// make sure we transform certain classes in the first pass to avoid "unexpected" allocation locations
		ArrayList<Class> classes = new ArrayList<Class>();
		HashSet<Class> done = new HashSet<Class>();
		for (int pass = 1;; pass++) {
			classes.addAll(Arrays.asList(inst.getAllLoadedClasses()));
			List<ClassDefinition> cdl = new ArrayList<ClassDefinition>(classes.size());
			sb.setLength(0);
			sb.append("Retransforming classes pass #").append(pass).append("...");
			log(sb);
			for (Class clazz : classes) {
				if (clazz.isArray())
					continue;
				if (!done.add(clazz))
					continue;
				String name = clazz.getName().replace('.', '/');
				sb.setLength(0);
				sb.append("/").append(name).append(".class");
				// trick to remove "unexpected" allocation location here
				InputStream is = clazz.getResourceAsStream(new String(sb));
				if (is == null) {
					sb.setLength(0);
					sb.append("Cannot retransform ").append(name);
					log(sb);
					continue;
				}
				ByteArrayOutputStream baos = new ByteArrayOutputStream();
				copy(is, baos);
				is.close();
				byte[] result = transformer.transform(
					clazz.getClassLoader(), name, clazz, clazz.getProtectionDomain(),
					baos.toByteArray());
				if (result != null)
					cdl.add(new ClassDefinition(clazz, result));
			}
			classes.clear();
			if (cdl.isEmpty())
				break; // all classes were redefined
			sb.setLength(0);
			sb.append("Redefining classes pass #").append(pass).append("...");
			log(sb);

			if (config.isVerboseRedefinition()) {
				for (ClassDefinition cd : cdl) {
					String name = cd.getDefinitionClass().getName();
					sb.setLength(0);
					sb.append("Redefining class ").append(name);
					log(sb);
					try {
						inst.redefineClasses(new ClassDefinition[] {cd});
					} catch (Exception e) {
						sb.setLength(0);
						sb.append("Failed to redefine class ").append(name).append(": ").append(e);
						log(sb);
					}
				}
			} else {
				inst.redefineClasses(cdl.toArray(new ClassDefinition[cdl.size()]));
			}
		}
		inst.addTransformer(transformer);
		log("Done redefining, transformer installed");

		// dumping
		log("Making first dump...");
		Dumper dumper = new Dumper(config, start);
		dumper.makeDump(false);

		DumpPeriodicThread dpt = null;
		if (config.getTime() > 0) {
			log("Starting dumper thread...");
			dpt = new DumpPeriodicThread(dumper, config.getTime());
			dpt.start();
		}

		long finish = System.currentTimeMillis();
		long trtime = AProfRegistry.getTime();
		log("Attaching shutdown hook...");
		Runtime.getRuntime().addShutdownHook(new DumpShutdownThread(dumper, finish, trtime, dpt));

		// listening on port
		if (config.getPort() > 0) {
			sb.setLength(0);
			sb.append("Listening on port ");
			sb.append(config.getPort());
			log(sb);
			Thread t = new ConnectionListenerThread(config.getPort(), dumper);
			t.start();
		}

		// done
		sb.setLength(0);
		sb.append("Loaded in ").append(finish - start).append(" ms with ").append(trtime).
			append(" ms in transformer (").append(finish - start - trtime).append(" ms other)");
		Log.out.println(sb);
	}

	private void log(Object o) {
		if (config.isVerbose())
			Log.out.println(o);
	}

	private static void copy(InputStream in, OutputStream out) throws IOException {
		byte[] buf = new byte[4096];
		int n;
		while ((n = in.read(buf)) > 0)
			out.write(buf, 0, n);
	}
}
