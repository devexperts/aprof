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

import com.devexperts.aprof.AProfRegistry;
import com.devexperts.aprof.Configuration;
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
	private final StringBuilder sharedStringBuilder = new StringBuilder();

	public AProfTransformer(Configuration config) {
		this.config = config;
		AProfRegistry.addDirectCloneClass(TransformerUtil.OBJECT_CLASS_NAME);
	}

	public byte[] transform(ClassLoader loader, String binaryClassName,
							Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer)
			throws IllegalClassFormatException {
		config.reloadTrackedClasses();
		long start = System.currentTimeMillis();
		if (binaryClassName == null) {
			sharedStringBuilder.setLength(0);
			sharedStringBuilder.append("Cannot transform class with no name");
			describeClassLoader(sharedStringBuilder, loader);
			Log.out.println(sharedStringBuilder);
			return null;
		}
		String cname = binaryClassName.replace('/', '.');
		for (String s : config.getExcludedClasses()) {
			if (cname.equals(s)) {
				sharedStringBuilder.setLength(0);
				sharedStringBuilder.append("Skipping transformation of excluded class: ");
				sharedStringBuilder.append(cname);
				describeClassLoader(sharedStringBuilder, loader);
				Log.out.println(sharedStringBuilder);
				return null;
			}
		}
		int classNo = AProfRegistry.incrementCount();
		if (config.isVerbose()) {
			synchronized (sharedStringBuilder) {
				describeTransformation(sharedStringBuilder, classNo, cname);
				describeClassLoader(sharedStringBuilder, loader);
				Log.out.println(sharedStringBuilder);
			}
		}
		try {
			int flags = (config.isSkipDebug() ? ClassReader.SKIP_DEBUG : 0) + ClassReader.EXPAND_FRAMES;

			ClassReader cr = new ClassReader(classfileBuffer);
			int javaVersion = cr.readShort(6);
			ClassWriter cw = javaVersion >= Opcodes.V1_7 ?
				new FrameClassWriter(cr) :
				new ClassWriter(ClassWriter.COMPUTE_MAXS);

			List<Context> methodContexts = new ArrayList<Context>();
			ClassVisitor classAnalyzer = new ClassAnalyzer(new EmptyClassVisitor(), binaryClassName, cname, methodContexts);
			cr.accept(classAnalyzer, flags);

			ClassVisitor classTransformer = new ClassTransformer(cw, cname, methodContexts);
			cr.accept(classTransformer, flags);
			byte[] bytes = cw.toByteArray();
			dumpClass(binaryClassName, classNo, cname, bytes);
			return bytes;
		} catch (Throwable t) {
			synchronized (sharedStringBuilder) {
				describeTransformation(sharedStringBuilder, classNo, cname);
				describeClassLoader(sharedStringBuilder, loader);
				sharedStringBuilder.append(" failed with error: ");
				sharedStringBuilder.append(t.toString());
				Log.out.println(sharedStringBuilder);
			}
			t.printStackTrace();
			return null;
		} finally {
			AProfRegistry.incrementTime(System.currentTimeMillis() - start);
		}
	}

	private static void describeTransformation(StringBuilder sb, int classNo, String cname) {
		sb.setLength(0);
		sb.append("Transforming class #");
		sb.append(classNo);
		sb.append(": ");
		sb.append(cname);
	}

	private void dumpClass(String binaryClassName, int classNo, String cname, byte[] bytes) {
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
			synchronized (sharedStringBuilder) {
				describeTransformation(sharedStringBuilder, classNo, cname);
				sharedStringBuilder.append(" dump failed with error: ");
				sharedStringBuilder.append(e.toString());
				Log.out.println(sharedStringBuilder);
			}
			e.printStackTrace();
		}
	}

	private static void describeClassLoader(StringBuilder sharedStringBuilder, ClassLoader loader) {
		if (loader != null) {
			sharedStringBuilder.append(" [in ");
			String lcname = loader.getClass().getName();
			String lstr = loader.toString();
			if (lstr.startsWith(lcname)) {
				sharedStringBuilder.append(lstr);
			} else {
				sharedStringBuilder.append(lcname);
				sharedStringBuilder.append(": ");
				sharedStringBuilder.append(lstr);
			}
			sharedStringBuilder.append("]");
		}
	}

	private class ClassAnalyzer extends ClassVisitor {
		private final String binaryClassName;
		private final String locationClass;
		private final boolean isNormal;
		private final String cname;
		private final List<Context> contexts;

		public ClassAnalyzer(final ClassVisitor cv, String binaryClassName, String cname, List<Context> contexts) {
			super(Opcodes.ASM4, cv);
			this.binaryClassName = binaryClassName;
			this.locationClass = AProfRegistry.normalize(cname);
			this.isNormal = AProfRegistry.isNormal(cname);
			this.cname = cname;
			this.contexts = contexts;
		}

		@Override
		public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
			super.visit(version, access, name, signature, superName, interfaces);
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
		public void visit(final int version, final int access, final String name, final String signature, final String superName, final String[] interfaces) {
			super.visit(Math.max(TransformerUtil.MIN_CLASS_VERSION, version), access, name, signature, superName, interfaces);
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

	private class EmptyClassVisitor extends ClassVisitor {
		public EmptyClassVisitor() {
			super(Opcodes.ASM4);
		}
	}

	private class FrameClassWriter extends ClassWriter {
		private FrameClassWriter(ClassReader classReader) {
			super(classReader, ClassWriter.COMPUTE_MAXS + ClassWriter.COMPUTE_FRAMES);
		}

		/**
		 * The reason of overriding is to avoid ClassCircularityError which occurs during processing of classes related
		 * to java.util.TimeZone
		 */
		@Override
		protected String getCommonSuperClass(String type1, String type2) {
			ClassLoader classLoader = getClass().getClassLoader();
			ClassInfo c, d;
			try {
				c = new ClassInfo(type1, classLoader);
				d = new ClassInfo(type2, classLoader);
			} catch (Throwable e) {
				throw new RuntimeException(e);
			}

			if (c.isAssignableFrom(d)) {
				return type1;
			}

			if (d.isAssignableFrom(c)) {
				return type2;
			}

			if (c.isInterface() || d.isInterface()) {
				return "java/lang/Object";
			} else {
				do {
					c = c.getSuperclass();
				} while (c != null && !c.isAssignableFrom(d));

				return c == null ? "java/lang/Object" : c.getType().getInternalName();
			}
		}
	}
}
