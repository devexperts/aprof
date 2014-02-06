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
public class AProfTransformer implements TransformerAnalyzer {
	private final Configuration config;
	private final StringBuilder sharedStringBuilder = new StringBuilder();
	private final Map<String, ClassHierarchy> classHierarchyMap = new HashMap<String, ClassHierarchy>();

	public AProfTransformer(Configuration config) {
		this.config = config;
		AProfRegistry.addDirectCloneClass(TransformerUtil.OBJECT_CLASS_NAME);
	}

	public synchronized ClassHierarchy getClassHierarchy(String className, ClassLoader loader) {
		ClassHierarchy result = classHierarchyMap.get(className);
		if (result != null)
			return result;
		result = buildClassHierarchy(className, loader);
		if (result != null)
			classHierarchyMap.put(className, result);
		return result;
	}

	private ClassHierarchy buildClassHierarchy(String className, ClassLoader loader) {
		try {
			String classFileName = className.replace('.', '/') + ".class";
			InputStream in;
			if (loader == null)
				in = getClass().getResourceAsStream("/" + classFileName);
			else
				in = loader.getResourceAsStream(classFileName);
			if (in == null)
				return null;
			ClassHierarchyVisitor visitor = new ClassHierarchyVisitor();
			try {
				ClassReader cr = new ClassReader(in);
				cr.accept(visitor, ClassReader.SKIP_DEBUG + ClassReader.SKIP_FRAMES + ClassReader.SKIP_CODE);
			} finally {
                in.close();
			}
			ClassHierarchy result = visitor.result;
			Set<String> visitedClasses = new HashSet<String>();
			visitedClasses.add(classFileName);
			result.getAllVirtualMethods().addAll(result.getDeclaredVirtualMethods());
			processInheritedMethods(result, result.getSuperClass(), loader, visitedClasses);
			for (String intf : result.getDeclaredInterfaces())
				processInheritedMethods(result, intf, loader, visitedClasses);
			return result;
		} catch (Throwable t) {
			log(0, "Failed to load class", className, loader, t);
			return null;
		}
	}

	private void processInheritedMethods(ClassHierarchy result, String className, ClassLoader loader, Set<String> visitedClasses) {
		if (className == null)
			return;
		if (!visitedClasses.add(className))
			return; // just in case of circularity
		ClassHierarchy inherits = getClassHierarchy(className, loader);
		if (inherits == null)
			return; // just ignore super classes that cannot be loaded
		result.getAllVirtualMethods().addAll(inherits.getAllVirtualMethods());
	}

	public byte[] transform(ClassLoader loader, String binaryClassName,
							Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer)
			throws IllegalClassFormatException {
		// always track invocations of transform method as a separate location
		LocationStack locationStack = LocationStack.get();
		LocationStack savedCopy = locationStack.pushStackForTransform(AProfRegistry.TRANSFORM_LOC);
		try {
			return transformImpl(loader, binaryClassName, classBeingRedefined, protectionDomain, classfileBuffer);
		} finally {
			locationStack.popStack(savedCopy);
		}
	}

	private byte[] transformImpl(ClassLoader loader, String binaryClassName,
						Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer)
			throws IllegalClassFormatException {
		// update tracking configuration under this (potentially new) class loader
		config.analyzeTrackedClasses(loader, this);
		// start transformation
		long start = System.currentTimeMillis();
		if (binaryClassName == null) {
			log(0, "Cannot transform class with no name", null, loader, null);
			return null;
		}
		String cname = binaryClassName.replace('/', '.');
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
			ClassReader cr = new ClassReader(classfileBuffer);

			// 1ST PASS: ANALYZE CLASS
			ClassAnalyzer classAnalyzer = new ClassAnalyzer(binaryClassName, cname);
			cr.accept(classAnalyzer, ClassReader.SKIP_DEBUG + ClassReader.SKIP_FRAMES);

			// check if transformation is needed
			boolean transformationNeeded = false;
			for (Context methodContext : classAnalyzer.contexts) {
				if (methodContext.isTransformationNeeded()) {
					transformationNeeded = true;
					break;
				}
			}
			if (!transformationNeeded)
				return classfileBuffer; // don't transform classes that don't need transformation

			// 2ST PASS: TRANSFORM CLASS
			boolean computeFrames = compareVersion(classAnalyzer.classVersion, Opcodes.V1_6) >= 0;
			ClassWriter cw = computeFrames ?
				new FrameClassWriter(cr) :
				new ClassWriter(ClassWriter.COMPUTE_MAXS);
			ClassVisitor classTransformer = new ClassTransformer(cw, cname, classAnalyzer.contexts);
			int transformFlags =
				(config.isSkipDebug() ? ClassReader.SKIP_DEBUG : 0) +
				(computeFrames ? ClassReader.SKIP_FRAMES : 0);
			cr.accept(classTransformer, transformFlags);

			// Convert transformed class to byte array, dump (if needed) and return
			byte[] bytes = cw.toByteArray();
			dumpClass(binaryClassName, classNo, cname, loader, bytes);
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
			if (loader != null) {
				sharedStringBuilder.append(" [in ");
				sharedStringBuilder.append(loader.getClass().getName());
				sharedStringBuilder.append('@');
				sharedStringBuilder.append(System.identityHashCode(loader));
				sharedStringBuilder.append("]");
			}
			if (error != null) {
				sharedStringBuilder.append(" with error ");
				sharedStringBuilder.append(error);
			}
			Log.out.println(sharedStringBuilder);
			if (error != null)
				error.printStackTrace(Log.out);
		}
	}

	private int compareVersion(int version1, int version2) {
		if (major(version1) > major(version2))
			return 1;
		if (major(version1) < major(version2))
			return -1;
		if (minor(version1) > minor(version2))
			return 1;
		if (minor(version1) < minor(version2))
			return -1;
		return 0;
	}

	private int minor(int version) {
		return version >>> 16;
	}

	private int major(int version) {
		return version & 0xffff;
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

	private static class ClassHierarchyVisitor extends ClassVisitor {
		final ClassHierarchy result = new ClassHierarchy();

		public ClassHierarchyVisitor() {
			super(Opcodes.ASM4);
		}

		@Override
		public void visit(int version, int access, String name, String signature, String superName,
			String[] interfaces)
		{
	          if (superName != null)
	            result.setSuperClass(superName.replace('/', '.'));
			if (interfaces != null)
				for (String it : interfaces)
					result.getDeclaredInterfaces().add(it.replace('/', '.'));
		}

		@Override
		public MethodVisitor visitMethod(int access, String name, String desc, String signature,
			String[] exceptions)
		{
			if ((access & Opcodes.ACC_STATIC) == 0 &&
				(access & Opcodes.ACC_PRIVATE) == 0 &&
				(access & Opcodes.ACC_FINAL) == 0 &&
				!name.equals(TransformerUtil.INIT))
			{
				result.getDeclaredVirtualMethods().add(name);
			}
			return null;
		}
	}

	private class ClassAnalyzer extends ClassVisitor {
		private final String binaryClassName;
		private final String locationClass;
		private final boolean isNormal;
		private final String cname;

		final List<Context> contexts = new ArrayList<Context>();
		int classVersion;

		public ClassAnalyzer(String binaryClassName, String cname) {
			super(Opcodes.ASM4);
			this.binaryClassName = binaryClassName;
			this.locationClass = AProfRegistry.normalize(cname);
			this.isNormal = AProfRegistry.isNormal(cname);
			this.cname = cname;
		}

		@Override
		public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
			super.visit(version, access, name, signature, superName, interfaces);
			classVersion = version;
			AProfRegistry.registerDatatypeInfo(locationClass);
			if (superName != null && isNormal && AProfRegistry.isDirectCloneClass(superName.replace('/', '.')))
				// candidate for direct clone
				AProfRegistry.addDirectCloneClass(locationClass);
		}

		@Override
		public MethodVisitor visitMethod(final int access, final String mname, final String desc, final String signature, final String[] exceptions) {
			if (isNormal && ((access & Opcodes.ACC_STATIC) == 0) && !locationClass.equals(TransformerUtil.OBJECT_CLASS_NAME) &&
					mname.equals(TransformerUtil.CLONE) && desc.equals(TransformerUtil.NOARG_RETURNS_OBJECT)) {
				// no -- does not implement clone directly
				AProfRegistry.removeDirectCloneClass(locationClass);
			}
			Context context = new Context(config, binaryClassName, cname, mname, desc, access);
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
			if (compareVersion(version, TransformerUtil.MIN_CLASS_VERSION) < 0)
				version = TransformerUtil.MIN_CLASS_VERSION;
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
}
