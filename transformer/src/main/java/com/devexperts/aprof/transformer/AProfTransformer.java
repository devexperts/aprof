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

package com.devexperts.aprof.transformer;

import java.io.*;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.security.ProtectionDomain;
import java.util.*;

import com.devexperts.aprof.*;
import com.devexperts.aprof.util.Log;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.*;

/**
 * @author Roman Elizarov
 * @author Dmitry Paraschenko
 * @author Denis Davydov
 */
public class AProfTransformer implements ClassFileTransformer {
	private final Configuration config;
	private final ClassInfoCache ciCache;
	private final StringBuilder sharedStringBuilder = new StringBuilder();

	public AProfTransformer(Configuration config) {
		this.config = config;
		ciCache = new ClassInfoCache(config);
		AProfRegistry.addDirectCloneClass(TransformerUtil.OBJECT_CLASS_NAME);
	}

	public byte[] transform(ClassLoader loader, String internalClassName,
							Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classFileBuffer)
			throws IllegalClassFormatException
	{
		// always track invocations of transform method as a separate location
		LocationStack locationStack = LocationStack.get();
		LocationStack savedCopy = locationStack.pushStackForTransform(AProfRegistry.TRANSFORM_LOC);
		try {
			return transformImpl(loader, internalClassName, classBeingRedefined, protectionDomain, classFileBuffer);
		} finally {
			locationStack.popStack(savedCopy);
		}
	}

	private byte[] transformImpl(ClassLoader loader, String internalClassName,
						Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classFileBuffer)
			throws IllegalClassFormatException
	{
		// measure time spent
		long start = System.currentTimeMillis();

		// init class info map for this classloader (if needed)
		ClassInfoMap classInfoMap = ciCache.getOrInitClassInfoMap(loader);

		// start transformation
		if (internalClassName == null) {
			log(0, "Cannot transform class with no name", null, loader, null);
			return null;
		}
		String cname = internalClassName.replace('/', '.');
		for (String s : config.getExcludedClasses()) {
			if (cname.equals(s)) {
				log(0, "Skipping transformation of excluded class", cname, loader, null);
				return null;
			}
		}
		int classNo = AProfRegistry.incrementCount();
		if (config.isVerbose())
			log(classNo, null, cname, loader, null);
		try {
			ClassReader cr = new ClassReader(classFileBuffer);

			// ---- 1ST PASS: ANALYZE CLASS ----

			// Also build class info if we don't have it yet in cache
			ClassInfo classInfo = classInfoMap.get(internalClassName);
			ClassInfoVisitor classInfoVisitor = classInfo == null ?
				new ClassInfoVisitor(classInfoMap.isInitTrackedClasses()) : null;
			ClassAnalyzer classAnalyzer = new ClassAnalyzer(internalClassName, loader, cname, classInfoVisitor);
			cr.accept(classAnalyzer, ClassReader.SKIP_DEBUG + ClassReader.SKIP_FRAMES);
			if (classInfoVisitor != null)
				classInfoMap.put(internalClassName, classInfoVisitor.result);

			// check if transformation is needed
			boolean transformationNeeded = false;
			for (Context methodContext : classAnalyzer.contexts) {
				if (methodContext.isTransformationNeeded()) {
					transformationNeeded = true;
					break;
				}
			}
			if (!transformationNeeded)
				return null; // don't transform classes that don't need transformation

			// ---- 2ND PASS: TRANSFORM CLASS ----

			boolean computeFrames = classAnalyzer.classVersion >= Opcodes.V1_6 && !config.isNoFrames();
			ClassWriter cw = computeFrames ?
				new FrameClassWriter(cr, loader) :
				new ClassWriter(ClassWriter.COMPUTE_MAXS);
			ClassVisitor classTransformer = new ClassTransformer(cw, cname, classAnalyzer.contexts);
			int transformFlags =
				(config.isSkipDebug() ? ClassReader.SKIP_DEBUG : 0) +
				(config.isNoFrames() || computeFrames ? ClassReader.SKIP_FRAMES : 0);
			cr.accept(classTransformer, transformFlags);

			// Convert transformed class to byte array, dump (if needed) and return
			byte[] bytes = cw.toByteArray();
			dumpClass(internalClassName, classNo, cname, loader, bytes);
			return bytes;
		} catch (Throwable t) {
			log(classNo, "failed", cname, loader, t);
			return null;
		} finally {
			AProfRegistry.incrementTime(System.currentTimeMillis() - start);
		}
	}

	private void log(int classNo, String message, String cname, ClassLoader loader, Throwable error) {
		synchronized (sharedStringBuilder) {
			sharedStringBuilder.setLength(0);
			if (classNo != 0) {
				sharedStringBuilder.append("Transforming class");
				sharedStringBuilder.append(" #");
				sharedStringBuilder.append(classNo);
			}
			if (message != null) {
				sharedStringBuilder.append(' ');
				sharedStringBuilder.append(message);
			}
			if (cname != null) {
				sharedStringBuilder.append(": ");
				sharedStringBuilder.append(cname);
			}
			TransformerUtil.describeClassLoaderForLog(sharedStringBuilder, loader);
			if (error != null) {
				sharedStringBuilder.append(" with error ");
				sharedStringBuilder.append(error);
			}
			Log.out.println(sharedStringBuilder);
			if (error != null)
				error.printStackTrace(Log.out);
		}
	}

	private void dumpClass(String binaryClassName, int classNo, String cname, ClassLoader loader, byte[] bytes) {
		String dir = config.getDumpClassesDir();
		if (dir.length() == 0)
			return;
		File file = new File(dir, binaryClassName + ".class");
		file.getParentFile().mkdirs();
		try {
			FileOutputStream out = new FileOutputStream(file);
			try {
				out.write(bytes);
			} finally {
	            out.close();
			}
		} catch (IOException e) {
			log(classNo, "failed", cname, loader, e);
		}
	}

	private class ClassAnalyzer extends ClassVisitor {
		private final String binaryClassName;
		private final ClassLoader loader;
		private final String locationClass;
		private final boolean isNormal;
		private final String cname;

		final List<Context> contexts = new ArrayList<Context>();
		int classVersion;

		public ClassAnalyzer(String binaryClassName, ClassLoader loader, String cname, ClassVisitor cv) {
			super(Opcodes.ASM4, cv);
			this.binaryClassName = binaryClassName;
			this.loader = loader;
			this.locationClass = AProfRegistry.normalize(cname);
			this.isNormal = AProfRegistry.isNormal(cname);
			this.cname = cname;
		}

		@Override
		public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
			// chain to ClassInfoVisitor if needed
			super.visit(version, access, name, signature, superName, interfaces);
			// analyze class
			classVersion = version & TransformerUtil.MAJOR_VERSION_MASK;
			AProfRegistry.registerDatatypeInfo(locationClass);
			if (superName != null && isNormal && AProfRegistry.isDirectCloneClass(superName.replace('/', '.')))
				// candidate for direct clone
				AProfRegistry.addDirectCloneClass(locationClass);
		}

		@Override
		public MethodVisitor visitMethod(final int access, final String mname, final String desc, final String signature, final String[] exceptions) {
			// chain to ClassInfoVisitor if needed
			super.visitMethod(access, mname, desc, signature, exceptions);
			// analyze method
			if (isNormal && ((access & Opcodes.ACC_STATIC) == 0) && !locationClass.equals(TransformerUtil.OBJECT_CLASS_NAME) &&
					mname.equals(TransformerUtil.CLONE) && desc.equals(TransformerUtil.NOARG_RETURNS_OBJECT)) {
				// no -- does not implement clone directly
				AProfRegistry.removeDirectCloneClass(locationClass);
			}
			Context context = new Context(config, ciCache, loader, binaryClassName, cname, mname, desc);
			contexts.add(context);
			return new MethodAnalyzer(new GeneratorAdapter(new EmptyMethodVisitor(), access, mname, desc), context);
		}
	}

	private class ClassTransformer extends ClassVisitor {
		private final Iterator<Context> contextIterator;

		public ClassTransformer(final ClassVisitor cv, String cname, List<Context> contexts) {
			super(Opcodes.ASM4, cv);
			this.contextIterator = contexts.iterator();
		}

		@Override
		public void visit(int version, final int access, final String name, final String signature, final String superName, final String[] interfaces) {
			// roll-forward version to MIN_CLASS_VERSION so that we can use ldc <class-constant> instruction,
			// but keep deprecated flag intact.
			if ((version & TransformerUtil.MAJOR_VERSION_MASK) < TransformerUtil.MIN_CLASS_VERSION)
				version = TransformerUtil.MIN_CLASS_VERSION | (version & Opcodes.ACC_DEPRECATED);
			super.visit(version, access, name, signature, superName, interfaces);
		}

		@Override
		public MethodVisitor visitMethod(final int access, final String mname, final String desc, final String signature, final String[] exceptions) {
			MethodVisitor visitor = super.visitMethod(access, mname, desc, signature, exceptions);
			visitor = new TryCatchBlockSorter(visitor, access, mname, desc, signature, exceptions);
			Context context = contextIterator.next();
			visitor = new MethodTransformer(new GeneratorAdapter(visitor, access, mname, desc), context);
			visitor = new JSRInlinerAdapter(visitor, access, mname, desc, signature, exceptions);
			return visitor;
		}

		@Override
		public void visitEnd() {
			super.visitEnd();
			assert !contextIterator.hasNext();
		}
	}

	private class EmptyMethodVisitor extends MethodVisitor {
		public EmptyMethodVisitor() {
			super(Opcodes.ASM4);
		}
	}

	class FrameClassWriter extends ClassWriter {
		// internalClassName -> ClassInfo
		private final ClassLoader loader;

		FrameClassWriter(ClassReader classReader, ClassLoader loader) {
			super(classReader, ClassWriter.COMPUTE_MAXS + ClassWriter.COMPUTE_FRAMES);
			this.loader = loader;
		}

		/**
		 * The reason of overriding is to avoid ClassCircularityError which occurs during processing of classes related
		 * to java.util.TimeZone and use cache of ClassInfo.
		 */
		@Override
		protected String getCommonSuperClass(String type1, String type2) {
			ClassInfo c = ciCache.getOrBuildRequiredClassInfo(type1, loader);
			ClassInfo d = ciCache.getOrBuildRequiredClassInfo(type2, loader);

			if (c.isAssignableFrom(d, ciCache, loader))
				return type1;
			if (d.isAssignableFrom(c, ciCache, loader))
				return type2;

			if (c.isInterface() || d.isInterface()) {
				return TransformerUtil.OBJECT;
			} else {
				do {
					c = c.getSuperclassInfo(ciCache, loader);
				} while (c != null && !c.isAssignableFrom(d, ciCache, loader));

				return c == null ? TransformerUtil.OBJECT : c.getInternalName();
			}
		}
	}
}
