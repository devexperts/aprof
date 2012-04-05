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

package com.devexperts.aprof.transformer;

import com.devexperts.aprof.AProfRegistry;
import com.devexperts.aprof.Configuration;
import com.devexperts.aprof.LocationStack;
import com.devexperts.aprof.util.Log;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.EmptyVisitor;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.JSRInlinerAdapter;
import org.objectweb.asm.commons.TryCatchBlockSorter;
import org.objectweb.asm.util.CheckClassAdapter;
import org.objectweb.asm.util.CheckMethodAdapter;

import java.lang.instrument.*;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * @author Roman Elizarov
 * @author Dmitry Paraschenko
 */
@SuppressWarnings({"UnusedDeclaration"})
public class AProfTransformer implements ClassFileTransformer {
	private static final int TRANSFORM_LOC = AProfRegistry.registerLocation(AProfTransformer.class.getCanonicalName() + ".transform");

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
	private final StringBuilder shared_sb = new StringBuilder();

	public AProfTransformer(Configuration config) {
		this.config = config;
		AProfRegistry.addDirectCloneClass(OBJECT_CLASS_NAME);
	}

	public byte[] transform(ClassLoader loader, String className,
							Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer)
			throws IllegalClassFormatException {
		LocationStack.markInternalInvokedMethod(TRANSFORM_LOC);
		config.reloadTrackedClasses();
		long start = System.currentTimeMillis();
		String cname = className.replace('/', '.');
		for (String s : config.getExcludedClasses())
			if (cname.equals(s))
				return null;
		int class_no = AProfRegistry.incrementCount();
		if (!config.isQuiet()) {
			synchronized (shared_sb) {
				shared_sb.setLength(0);
				shared_sb.append("Transforming class #");
				shared_sb.append(class_no);
				shared_sb.append(": ");
				shared_sb.append(cname);
				if (loader != null) {
					shared_sb.append(" [in ");
					String lcname = loader.getClass().getName();
					String lstr = loader.toString();
					if (lstr.startsWith(lcname))
						shared_sb.append(lstr);
					else {
						shared_sb.append(lcname);
						shared_sb.append(": ");
						shared_sb.append(lstr);
					}
					shared_sb.append("]");
				}
				Log.out.println(shared_sb);
			}
		}
		try {
			int flags = (config.isSkipDebug() ? ClassReader.SKIP_DEBUG : 0) + ClassReader.EXPAND_FRAMES;

			ClassReader cr = new ClassReader(classfileBuffer);
			ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_MAXS);

			List<Context> method_contexts = new ArrayList<Context>();
			ClassVisitor classAnalyzer = new ClassAnalyzer(new EmptyVisitor(), cname, method_contexts);
			cr.accept(classAnalyzer, flags);

			ClassVisitor classTransformer = new ClassTransformer(cw, cname, method_contexts);
			classTransformer = new CheckClassAdapter(classTransformer);
			cr.accept(classTransformer, flags);
			return cw.toByteArray();
		} catch (Throwable t) {
			synchronized (shared_sb) {
				shared_sb.setLength(0);
				shared_sb.append("Transforming class #");
				shared_sb.append(class_no);
				shared_sb.append(" (");
				shared_sb.append(cname);
				shared_sb.append(") failed with error: ");
				shared_sb.append(t.getLocalizedMessage());
				Log.out.println(shared_sb);
			}
			t.printStackTrace();
			return null;
		} finally {
			AProfRegistry.incrementTime(System.currentTimeMillis() - start);
			LocationStack.unmarkInternalInvokedMethod();
		}
	}

	private class ClassAnalyzer extends ClassAdapter {
		private final String cname;
		private final List<Context> contexts;

		public ClassAnalyzer(final ClassVisitor cv, String cname, List<Context> contexts) {
			super(cv);
			this.cname = cname;
			this.contexts = contexts;
		}

		@Override
		public MethodVisitor visitMethod(final int access, final String mname, final String desc, final String signature, final String[] exceptions) {
			Context context = new Context(config, cname, mname, desc, access);
			contexts.add(context);
			return new MethodAnalyzer(new GeneratorAdapter(new EmptyVisitor(), access, mname, desc), context);
		}
	}

	private class ClassTransformer extends ClassAdapter {
		private final String cname;
		private final Iterator<Context> context_iterator;

		public ClassTransformer(final ClassVisitor cv, String cname, List<Context> contexts) {
			super(cv);
			this.cname = AProfRegistry.normalize(cname);
			this.context_iterator = contexts.iterator();
		}

		@Override
		public void visit(final int version, final int access, final String name, final String signature, final String superName, final String[] interfaces) {
			super.visit(version, access, name, signature, superName, interfaces);
			AProfRegistry.registerDatatypeInfo(cname);
			if (superName != null && AProfRegistry.isDirectCloneClass(superName.replace('/', '.')))
				// candidate for direct clone
				AProfRegistry.addDirectCloneClass(cname);

		}

		@Override
		public MethodVisitor visitMethod(final int access, final String mname, final String desc, final String signature, final String[] exceptions) {
			if (((access & Opcodes.ACC_STATIC) == 0) && !cname.equals(OBJECT_CLASS_NAME) &&
					mname.equals(CLONE) && desc.equals(NOARG_RETURNS_OBJECT)) {
				// no -- does not implement clone directly
				AProfRegistry.removeDirectCloneClass(cname);
			}
			MethodVisitor visitor = super.visitMethod(access, mname, desc, signature, exceptions);
			visitor = new CheckMethodAdapter(visitor);
			visitor = new TryCatchBlockSorter(visitor, access, mname, desc, signature, exceptions);
			Context context = context_iterator.next();
			visitor = new MethodTransformer(new GeneratorAdapter(visitor, access, mname, desc), context);
			visitor = new JSRInlinerAdapter(visitor, access, mname, desc, signature, exceptions);
			return visitor;
		}

		@Override
		public void visitEnd() {
			super.visitEnd();
			assert !context_iterator.hasNext();
		}
	}
}
