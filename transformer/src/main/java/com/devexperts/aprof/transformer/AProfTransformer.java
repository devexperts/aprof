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

import com.devexperts.aprof.AProfOps;
import com.devexperts.aprof.AProfRegistry;
import com.devexperts.aprof.Configuration;
import com.devexperts.aprof.LocationStack;
import com.devexperts.aprof.util.Log;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.AdviceAdapter;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.JSRInlinerAdapter;
import org.objectweb.asm.util.CheckClassAdapter;
import org.objectweb.asm.util.CheckMethodAdapter;

import java.lang.instrument.*;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Roman Elizarov
 */
@SuppressWarnings({"UnusedDeclaration"})
public class AProfTransformer implements ClassFileTransformer {
	private static final String APROF_OPS = "com/devexperts/aprof/AProfOps";
    private static final String APROF_OPS_INTERNAL = "com/devexperts/aprof/AProfOpsInternal";
    private static final String LOCATION_STACK = "com/devexperts/aprof/LocationStack";

    private static final String OBJECT_CLASS_NAME = "java.lang.Object";

    private static final String ACCESS_METHOD = "access$";

	private static final String INIT = "<init>";
	private static final String CLONE = "clone";

    private static final String NOARG_RETURNS_OBJECT = "()Ljava/lang/Object;";
    private static final String NOARG_RETURNS_LOCATION_STACK = "()Lcom/devexperts/aprof/LocationStack;";
    private static final String NOARG_VOID = "()V";
	private static final String INT_VOID = "(I)V";
	private static final String OBJECT_VOID = "(Ljava/lang/Object;)V";
	private static final String OBJECT_INT_VOID = "(Ljava/lang/Object;I)V";
	private static final String CLASS_INT_RETURNS_OBJECT = "(Ljava/lang/Class;I)Ljava/lang/Object;";
	private static final String CLASS_INT_ARR_RETURNS_OBJECT = "(Ljava/lang/Class;[I)Ljava/lang/Object;";

	private static final Type BOOLEAN_ARR_T = Type.getType(boolean[].class);
	private static final Type CHAR_ARR_T = Type.getType(char[].class);
	private static final Type FLOAT_ARR_T = Type.getType(float[].class);
	private static final Type DOUBLE_ARR_T = Type.getType(double[].class);
	private static final Type BYTE_ARR_T = Type.getType(byte[].class);
	private static final Type SHORT_ARR_T = Type.getType(short[].class);
	private static final Type INT_ARR_T = Type.getType(int[].class);
    private static final Type LONG_ARR_T = Type.getType(long[].class);

    private static final int TRANSFORM_LOC = AProfRegistry.registerLocation(AProfTransformer.class.getCanonicalName() + ".transform");


    private final Configuration config;

	private final StringBuilder shared_sb = new StringBuilder();

	public AProfTransformer(Configuration config) {
		this.config = config;
		AProfRegistry.addDirectCloneClass(OBJECT_CLASS_NAME);
	}

	private String getLocationString(String cname, String mname, String desc) {
		cname = cname.replace('/', '.'); // to make sure it can be called both on cname and owner descs.
		StringBuilder sb = new StringBuilder(cname.length() + 1 + mname.length());
		sb.append(cname).append(".").append(mname);
		String location = sb.toString();
		for (String s : config.getSignatureLocations())
			if (location.equals(s)) {
				convertDesc(sb, desc);
				location = sb.toString();
				break;
			}
		return location;
	}

	private void convertDesc(StringBuilder sb, String desc) {
		sb.append('(');
		Type[] types = Type.getArgumentTypes(desc);
		for (int i = 0; i < types.length; i++) {
			if (i > 0)
				sb.append(',');
			appendShortType(sb, types[i]);
		}
		sb.append(')');
		appendShortType(sb, Type.getReturnType(desc));
	}

	private void appendShortType(StringBuilder sb, Type type) {
		if (type == Type.VOID_TYPE)
			return;
		String s = type.getClassName();
		sb.append(s, s.lastIndexOf('.') + 1, s.length());
	}

	public byte[] transform(ClassLoader loader,	String className,
		Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer)
		throws IllegalClassFormatException
	{
        AProfOps.markInternalInvokedMethod(TRANSFORM_LOC);
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
            ClassReader cr = new ClassReader(classfileBuffer);
            ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_MAXS);
            cr.accept(new CheckClassAdapter(new AClassVisitor(cw, cname)), (config.isSkipDebug() ? ClassReader.SKIP_DEBUG : 0) + ClassReader.EXPAND_FRAMES);
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
            AProfOps.unmarkInternalInvokedMethod();
        }
	}

	class AClassVisitor extends ClassAdapter {
		private final String cname;

		public AClassVisitor(final ClassVisitor cv, String cname) {
			super(cv);
			this.cname = cname;
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
				mname.equals(CLONE) && desc.equals(NOARG_RETURNS_OBJECT))
			{
				// no -- does not implement clone directly
				AProfRegistry.removeDirectCloneClass(cname);
			}
			MethodVisitor visitor = super.visitMethod(access, mname, desc, signature, exceptions);
            visitor = new CheckMethodAdapter(visitor);
            visitor = new JSRInlinerAdapter(visitor, access, mname, desc, signature,  exceptions);
            visitor = new InvocationPointTracker(new GeneratorAdapter(visitor, access, mname, desc), cname, mname, desc);
            visitor = new InvokedMethodTracker(new GeneratorAdapter(visitor, access, mname, desc), access, cname, mname, desc);
			return visitor;
		}
	}


    private class InvokedMethodTracker extends AdviceAdapter {
        private final GeneratorAdapter mv;
        private final String cname;
        private final String mname;
        private final String invoked_method;
        private final boolean mark;

        private int location_stack = -1;

        protected InvokedMethodTracker(GeneratorAdapter mv, int access, String cname, String mname, String desc) {
            super(mv, access, mname, desc);
            this.mv = mv;
            this.cname = cname;
            this.mname = mname;
            this.invoked_method = getLocationString(cname, mname, desc);
            this.mark = !mname.startsWith(ACCESS_METHOD) && !AProfRegistry.isInternalClass(cname) && AProfRegistry.isLocationTracked(invoked_method);
        }

        @Override
        protected void onMethodEnter() {
            location_stack = mv.newLocal(Type.getType(LocationStack.class));
            mv.visitInsn(Opcodes.ACONST_NULL);
            mv.storeLocal(location_stack);
            if (mark) {
                visitMarkInvokedMethod();
            }
        }

        @Override
        protected void onMethodExit(int opcode) {
            if (mark) {
                visitUnmarkInvokedMethod();
            }
            if (cname.equals(OBJECT_CLASS_NAME) && mname.equals(INIT)) {
                visitObjectInit();
            }
        }

        private void pushLocationStack() {
            if (location_stack < 0) {
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, LOCATION_STACK, "get", NOARG_RETURNS_LOCATION_STACK);
                return;
            }

            Label done = new Label();
            mv.loadLocal(location_stack);
            mv.dup();
            mv.ifNonNull(done);
            mv.pop();
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, LOCATION_STACK, "get", NOARG_RETURNS_LOCATION_STACK);
            mv.dup();
            mv.storeLocal(location_stack);
            mv.visitLabel(done);
        }

        /**
         * @see com.devexperts.aprof.AProfOps#markInvokedMethod(int)
         */
		private void visitMarkInvokedMethod() {
            pushLocationStack();
            mv.push(AProfRegistry.registerLocation(invoked_method));
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, LOCATION_STACK, "addInvokedMethod", INT_VOID);
		}

        /**
         * @see com.devexperts.aprof.AProfOps#unmarkInvokedMethod()
         */
		private void visitUnmarkInvokedMethod() {
            pushLocationStack();
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, LOCATION_STACK, "removeInvokedMethod", NOARG_VOID);
    	}

        /**
         * @see com.devexperts.aprof.AProfOps#objectInit(Object)
         * @see com.devexperts.aprof.AProfOps#objectInitSize(Object)
         */
		private void visitObjectInit() {
			if (config.isUnknown()) {
				mv.loadThis();
				if (config.isSize()) {
					mv.visitMethodInsn(Opcodes.INVOKESTATIC, APROF_OPS, "objectInitSize", OBJECT_VOID);
                } else {
					mv.visitMethodInsn(Opcodes.INVOKESTATIC, APROF_OPS, "objectInit", OBJECT_VOID);
                }
			}
		}
    }

    private static class CatchBlock {
        private final Label start;
        private final Label end;
        private final Label handler;
        private final String type;

        public CatchBlock(Label start, Label end, Label handler, String type) {
            this.start = start;
            this.end = end;
            this.handler = handler;
            this.type = type;
        }
    }

	private class InvocationPointTracker extends MethodAdapter {
        private final GeneratorAdapter mv;
		private final String cname;
		private final String mname;
		private final String desc;
        private final List<CatchBlock> blocks = new ArrayList<CatchBlock>();

		private String _location;


		public InvocationPointTracker(GeneratorAdapter mv, String cname, String mname, String desc) {
			super(mv);
            this.mv = mv;
			this.cname = cname;
			this.mname = mname;
			this.desc = desc;
		}

		private String getLocation() {
			if (_location == null)
				_location = getLocationString(cname, mname, desc);
			return _location;
		}

        @Override
		public void visitTypeInsn(final int opcode, final String desc) {
            String name = desc.replace('/', '.');
			if (opcode == Opcodes.NEW) {
                visitAllocate(desc);
			}
			super.visitTypeInsn(opcode, desc);
            if (opcode == Opcodes.ANEWARRAY) {
                String array_name = name.startsWith("[") ? "[" + name : "[L" + name + ";";
                visitAllocateArray(array_name);
            }
		}

        @Override
		public void visitIntInsn(final int opcode, final int operand) {
			super.visitIntInsn(opcode, operand);
			if (opcode == Opcodes.NEWARRAY) {
				Type type;
				switch (operand) {
				case Opcodes.T_BOOLEAN:
					type = BOOLEAN_ARR_T;
					break;
				case Opcodes.T_CHAR:
					type = CHAR_ARR_T;
					break;
				case Opcodes.T_FLOAT:
					type = FLOAT_ARR_T;
					break;
				case Opcodes.T_DOUBLE:
					type = DOUBLE_ARR_T;
					break;
				case Opcodes.T_BYTE:
					type = BYTE_ARR_T;
					break;
				case Opcodes.T_SHORT:
					type = SHORT_ARR_T;
					break;
				case Opcodes.T_INT:
					type = INT_ARR_T;
					break;
				case Opcodes.T_LONG:
					type = LONG_ARR_T;
					break;
				default:
					return; // should not happen
				}
				visitAllocateArray(type.getDescriptor());
			}
		}

        @Override
		public void visitMultiANewArrayInsn(final String desc, final int dims) {
			super.visitMultiANewArrayInsn(desc, dims);
			visitAllocateArray(desc);
		}

        @Override
		public void visitMethodInsn(final int opcode, final String owner, final String name, final String desc) {
            if (AProfRegistry.isInternalClass(cname)) {
                super.visitMethodInsn(opcode, owner, name, desc);
                return;
            }

			// check if it is eligible object.clone call (that can get dispatched to actual Object.clone method
			boolean is_clone = opcode != Opcodes.INVOKESTATIC && name.equals(CLONE) && desc.equals(NOARG_RETURNS_OBJECT);
			boolean is_array_clone = is_clone && owner.startsWith("[");
			boolean is_object_clone = is_clone && AProfRegistry.isDirectCloneClass(owner.replace('/', '.'));

            String invoked_method = getLocationString(owner, name, desc);
            boolean is_method_tracked = AProfRegistry.isLocationTracked(invoked_method) && !mname.startsWith(ACCESS_METHOD);

            if (is_method_tracked) {
                Label start = new Label();
                Label end = new Label();
                Label handler = new Label();
                Label done = new Label();
                visitMarkInvocationPoint();
                mv.visitTryCatchBlock(start, end, handler, null);
                mv.visitLabel(start);
                super.visitMethodInsn(opcode, owner, name, desc);
                mv.visitLabel(end);
                visitUnmarkInvocationPoint();
                mv.goTo(done);
                mv.visitLabel(handler);
                int var = mv.newLocal(Type.getType(Object.class));
                mv.storeLocal(var);
                visitUnmarkInvocationPoint();
                mv.loadLocal(var);
                mv.throwException();
                mv.visitLabel(done);
            } else {
                super.visitMethodInsn(opcode, owner, name, desc);
            }

			if (opcode == Opcodes.INVOKEVIRTUAL && is_object_clone) {
				// INVOKEVIRTUAL needs runtime check of class that is being cloned
				visitAllocateReflectVClone(AProfRegistry.CLONE_SUFFIX);
			}
            if (opcode == Opcodes.INVOKESPECIAL && is_object_clone) {
    			// Object.clone via super.clone (does not need runtime check)
				visitAllocateReflect(AProfRegistry.CLONE_SUFFIX);
			}
            if (is_array_clone) {
    			// <array>.clone (usually via INVOKEVIRTUAL, but we don't care)
				visitAllocateReflect(AProfRegistry.CLONE_SUFFIX);
			}
            if (opcode == Opcodes.INVOKESTATIC && owner.equals("java/lang/reflect/Array") && name.equals("newInstance")
				&& (desc.equals(CLASS_INT_RETURNS_OBJECT) || desc.equals(CLASS_INT_ARR_RETURNS_OBJECT)))
			{
                // Array.newInstance
				visitAllocateReflect(AProfRegistry.ARRAY_NEWINSTANCE_SUFFIX);
			}
		}

        @Override
        public void visitTryCatchBlock(Label start, Label end, Label handler, String type) {
            blocks.add(new CatchBlock(start, end, handler, type));
        }

        @Override
        public void visitMaxs(int maxStack, int maxLocals) {
            for (CatchBlock block : blocks) {
                mv.visitTryCatchBlock(block.start, block.end, block.handler, block.type);
            }
            super.visitMaxs(maxStack, maxLocals);
        }

        private void pushAllocationPoint(String datatype) {
            mv.push(AProfRegistry.registerAllocationPoint(datatype, getLocation()));
        }

        /**
         * OPS implementation is chosen based on the class doing the allocation.
         *
         * @see com.devexperts.aprof.AProfOps#allocate(int)
         * @see com.devexperts.aprof.AProfOpsInternal#allocate(int)
         */
        private void visitAllocate(String desc) {
            if (config.isLocation()) {
                String ops_implementation = AProfRegistry.isInternalClass(cname) ? APROF_OPS_INTERNAL : APROF_OPS;

                String name = desc.replace('/', '.');
                pushAllocationPoint(name);
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, ops_implementation, "allocate", INT_VOID);
            }
        }

        /**
         * @see com.devexperts.aprof.AProfOps#markInvocationPoint(int)
         */
		private void visitMarkInvocationPoint() {
            mv.push(AProfRegistry.registerLocation(getLocation()));
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, APROF_OPS, "markInvocationPoint", INT_VOID);
		}

        /**
         * @see com.devexperts.aprof.AProfOps#unmarkInvocationPoint()
         */
        private void visitUnmarkInvocationPoint() {
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, APROF_OPS, "unmarkInvocationPoint", NOARG_VOID);
        }

        /**
         * OPS implementation is chosen based on the class doing the allocation.
         *
         * @see com.devexperts.aprof.AProfOps#allocate(int)
         * @see com.devexperts.aprof.AProfOps#allocateArraySize(boolean[], int)
         * @see com.devexperts.aprof.AProfOps#allocateArraySize(byte[], int)
         * @see com.devexperts.aprof.AProfOps#allocateArraySize(char[], int)
         * @see com.devexperts.aprof.AProfOps#allocateArraySize(short[], int)
         * @see com.devexperts.aprof.AProfOps#allocateArraySize(int[], int)
         * @see com.devexperts.aprof.AProfOps#allocateArraySize(long[], int)
         * @see com.devexperts.aprof.AProfOps#allocateArraySize(float[], int)
         * @see com.devexperts.aprof.AProfOps#allocateArraySize(double[], int)
         * @see com.devexperts.aprof.AProfOps#allocateArraySize(Object[], int)
         * @see com.devexperts.aprof.AProfOps#allocateArraySizeMulti(Object[], int)
         * @see com.devexperts.aprof.AProfOpsInternal#allocate(int)
         * @see com.devexperts.aprof.AProfOpsInternal#allocateArraySize(boolean[], int)
         * @see com.devexperts.aprof.AProfOpsInternal#allocateArraySize(byte[], int)
         * @see com.devexperts.aprof.AProfOpsInternal#allocateArraySize(char[], int)
         * @see com.devexperts.aprof.AProfOpsInternal#allocateArraySize(short[], int)
         * @see com.devexperts.aprof.AProfOpsInternal#allocateArraySize(int[], int)
         * @see com.devexperts.aprof.AProfOpsInternal#allocateArraySize(long[], int)
         * @see com.devexperts.aprof.AProfOpsInternal#allocateArraySize(float[], int)
         * @see com.devexperts.aprof.AProfOpsInternal#allocateArraySize(double[], int)
         * @see com.devexperts.aprof.AProfOpsInternal#allocateArraySize(Object[], int)
         * @see com.devexperts.aprof.AProfOpsInternal#allocateArraySizeMulti(Object[], int)
         */
		private void visitAllocateArray(String desc) {
			if (config.isArrays()) {
                if (AProfRegistry.isInternalClass(cname)) {
                    return;
                }
                String ops_implementation = AProfRegistry.isInternalClass(cname) ? APROF_OPS_INTERNAL : APROF_OPS;

				String name = desc.replace('/', '.');
				if (config.isSize()) {
					mv.dup();
					pushAllocationPoint(name);
					boolean is_multi = desc.lastIndexOf('[') > 0;
					boolean is_primitive = desc.length() == 2;
					StringBuilder sb = new StringBuilder();
					sb.append("(");
					if (is_primitive) {
						sb.append(desc);
                    } else {
						sb.append("[Ljava/lang/Object;");
                    }
                    sb.append("I)V");
					String mname = is_multi ? "allocateArraySizeMulti" : "allocateArraySize";
					mv.visitMethodInsn(Opcodes.INVOKESTATIC, ops_implementation, mname, sb.toString());
				} else {
					pushAllocationPoint(name);
					mv.visitMethodInsn(Opcodes.INVOKESTATIC, ops_implementation, "allocate", INT_VOID);
				}
			}
		}

        /**
         * @see com.devexperts.aprof.AProfOps#allocateReflect(Object, int)
         * @see com.devexperts.aprof.AProfOps#allocateReflectSize(Object, int)
         */
		private void visitAllocateReflect(String suffix) {
			if (config.isReflect()) {
                mv.dup();
                int loc = AProfRegistry.registerLocation(getLocation() + suffix);
                mv.push(loc);
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, APROF_OPS,
                    config.isSize() ? "allocateReflectSize" : "allocateReflect",
                    OBJECT_INT_VOID);
			}
		}

        /**
         * @see com.devexperts.aprof.AProfOps#allocateReflectVClone(Object, int)
         * @see com.devexperts.aprof.AProfOps#allocateReflectVCloneSize(Object, int)
         */
		private void visitAllocateReflectVClone(String suffix) {
			if (config.isReflect() && !AProfRegistry.isInternalClass(cname)) {
                mv.dup();
                int loc = AProfRegistry.registerLocation(getLocation() + suffix);
                mv.push(loc);
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, APROF_OPS,
                    config.isSize() ? "allocateReflectVCloneSize" : "allocateReflectVClone",
                    OBJECT_INT_VOID);
			}
		}
	}
}