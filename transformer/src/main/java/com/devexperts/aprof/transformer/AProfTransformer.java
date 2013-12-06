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

package com.devexperts.aprof.transformer;

import com.devexperts.aprof.AProfRegistry;
import com.devexperts.aprof.Configuration;
import com.devexperts.aprof.util.Log;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.JSRInlinerAdapter;
import org.objectweb.asm.commons.TryCatchBlockSorter;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * @author Roman Elizarov
 * @author Dmitry Paraschenko
 * @author Denis Davydov
 */
public class AProfTransformer implements ClassFileTransformer {
	static final String APROF_OPS = "com/devexperts/aprof/AProfOps";
	static final String APROF_OPS_INTERNAL = "com/devexperts/aprof/AProfOpsInternal";
	static final String LOCATION_STACK = "com/devexperts/aprof/LocationStack";

	static final String OBJECT_CLASS_NAME = "java.lang.Object";

	static final String ACCESS_METHOD = "access$";

	static final String OBJECT_INIT = "<init>";
	static final String CLONE = "clone";

	static final String NOARG_RETURNS_OBJECT = "()Ljava/lang/Object;";
	static final String NOARG_RETURNS_STACK = "()Lcom/devexperts/aprof/LocationStack;";
	static final String NOARG_VOID = "()V";
	static final String INT_VOID = "(I)V";
	static final String STACK_INT_VOID = "(Lcom/devexperts/aprof/LocationStack;I)V";
	static final String OBJECT_VOID = "(Ljava/lang/Object;)V";
	static final String OBJECT_INT_VOID = "(Ljava/lang/Object;I)V";
	static final String CLASS_INT_RETURNS_OBJECT = "(Ljava/lang/Class;I)Ljava/lang/Object;";
	static final String CLASS_INT_ARR_RETURNS_OBJECT = "(Ljava/lang/Class;[I)Ljava/lang/Object;";


	private final Configuration config;
	private final StringBuilder sharedStringBuilder = new StringBuilder();

	public AProfTransformer(Configuration config) {
		this.config = config;
		AProfRegistry.addDirectCloneClass(OBJECT_CLASS_NAME);
	}

	public byte[] transform(ClassLoader loader, String className,
							Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer)
			throws IllegalClassFormatException {
		config.reloadTrackedClasses();
		long start = System.currentTimeMillis();
		if (className == null) {
			sharedStringBuilder.setLength(0);
			sharedStringBuilder.append("Cannot transform class with no name");
			describeClassLoader(sharedStringBuilder, loader);
			Log.out.println(sharedStringBuilder);
			return null;
		}
		className = className.replace('/', '.');
		for (String s : config.getExcludedClasses()) {
			if (className.equals(s)) {
				sharedStringBuilder.setLength(0);
				sharedStringBuilder.append("Skipping transformation of excluded class: ");
				sharedStringBuilder.append(className);
				describeClassLoader(sharedStringBuilder, loader);
				Log.out.println(sharedStringBuilder);
				return null;
			}
		}
		int classNo = AProfRegistry.incrementCount();
		if (config.isVerbose()) {
			synchronized (sharedStringBuilder) {
				sharedStringBuilder.setLength(0);
				sharedStringBuilder.append("Transforming class #");
				sharedStringBuilder.append(classNo);
				sharedStringBuilder.append(": ");
				sharedStringBuilder.append(className);
				describeClassLoader(sharedStringBuilder, loader);
				Log.out.println(sharedStringBuilder);
			}
		}
		try {
			int flags = (config.isSkipDebug() ? ClassReader.SKIP_DEBUG : 0) + ClassReader.EXPAND_FRAMES;

			ClassReader cr = new ClassReader(classfileBuffer);
			int javaVersion = cr.readShort(6);
			ClassWriter cw =
					javaVersion == Opcodes.V1_7 ? new FrameClassWriter(cr) : new ClassWriter(ClassWriter.COMPUTE_MAXS);

			List<Context> methodContexts = new ArrayList<Context>();
			ClassVisitor classAnalyzer = new ClassAnalyzer(new EmptyClassVisitor(), className, methodContexts);
			cr.accept(classAnalyzer, flags);

			ClassVisitor classTransformer = new ClassTransformer(cw, className, methodContexts);
			cr.accept(classTransformer, flags);
			return cw.toByteArray();
		} catch (Throwable t) {
			synchronized (sharedStringBuilder) {
				sharedStringBuilder.setLength(0);
				sharedStringBuilder.append("Transforming class #");
				sharedStringBuilder.append(classNo);
				sharedStringBuilder.append(" (").append(className).append(")");
				describeClassLoader(sharedStringBuilder, loader);
				sharedStringBuilder.append(" failed with error: ");
				sharedStringBuilder.append(t.getLocalizedMessage());
				Log.out.println(sharedStringBuilder);
			}
			t.printStackTrace();
			return null;
		} finally {
			AProfRegistry.incrementTime(System.currentTimeMillis() - start);
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
		private final String cname;
		private final List<Context> contexts;

		public ClassAnalyzer(final ClassVisitor cv, String cname, List<Context> contexts) {
			super(Opcodes.ASM4, cv);
			this.cname = cname;
			this.contexts = contexts;
		}

		@Override
		public MethodVisitor visitMethod(final int access, final String mname, final String desc, final String signature, final String[] exceptions) {
			Context context = new Context(config, cname, mname, desc, access);
			contexts.add(context);
			return new MethodAnalyzer(new GeneratorAdapter(new EmptyMethodVisitor(), access, mname, desc), context);
		}
	}

	private class ClassTransformer extends ClassVisitor {
		private final String locationClass;
		private final boolean isNormal;
		private final Iterator<Context> contextIterator;

		public ClassTransformer(final ClassVisitor cv, String cname, List<Context> contexts) {
			super(Opcodes.ASM4, cv);
			this.isNormal = AProfRegistry.isNormal(cname);
			this.locationClass = AProfRegistry.normalize(cname);
			this.contextIterator = contexts.iterator();
		}

		@Override
		public void visit(final int version, final int access, final String name, final String signature, final String superName, final String[] interfaces) {
			super.visit(version, access, name, signature, superName, interfaces);
			AProfRegistry.registerDatatypeInfo(locationClass);
			if (superName != null && isNormal && AProfRegistry.isDirectCloneClass(superName.replace('/', '.')))
				// candidate for direct clone
				AProfRegistry.addDirectCloneClass(locationClass);

		}

		@Override
		public MethodVisitor visitMethod(final int access, final String mname, final String desc, final String signature, final String[] exceptions) {
			if (isNormal && ((access & Opcodes.ACC_STATIC) == 0) && !locationClass.equals(OBJECT_CLASS_NAME) &&
					mname.equals(CLONE) && desc.equals(NOARG_RETURNS_OBJECT)) {
				// no -- does not implement clone directly
				AProfRegistry.removeDirectCloneClass(locationClass);
			}
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
				return OBJECT_CLASS_NAME;
			} else {
				do {
					c = c.getSuperclass();
				} while (!c.isAssignableFrom(d));

				return c.getType().getInternalName();
			}
		}
	}
}
