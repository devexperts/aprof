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
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodAdapter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;

import java.util.ArrayList;
import java.util.List;

import static org.objectweb.asm.Opcodes.*;

/**
 * @author Dmitry Paraschenko
 */
class InvocationPointTracker extends MethodAdapter {
    private static final Type BOOLEAN_ARR_T = Type.getType(boolean[].class);
    private static final Type CHAR_ARR_T = Type.getType(char[].class);
    private static final Type FLOAT_ARR_T = Type.getType(float[].class);
    private static final Type DOUBLE_ARR_T = Type.getType(double[].class);
    private static final Type BYTE_ARR_T = Type.getType(byte[].class);
    private static final Type SHORT_ARR_T = Type.getType(short[].class);
    private static final Type INT_ARR_T = Type.getType(int[].class);
    private static final Type LONG_ARR_T = Type.getType(long[].class);

    private final GeneratorAdapter mv;
    private final Context context;

    private final List<CatchBlock> blocks = new ArrayList<CatchBlock>();

    public InvocationPointTracker(GeneratorAdapter mv, Context context) {
        super(mv);
        this.mv = mv;
        this.context = context;
    }

    @Override
    public void visitCode() {
        mv.visitCode();
        context.declareLocationStack(mv);
        if (context.isMethodTracked()) {
            visitMarkInvokedMethod();
        }
    }

    public void visitInsn(final int opcode) {
        switch (opcode) {
            case RETURN:
            case IRETURN:
            case FRETURN:
            case ARETURN:
            case LRETURN:
            case DRETURN:
            case ATHROW: {
                if (context.isMethodTracked()) {
                    visitUnmarkInvokedMethod();
                }
                if (context.isObjectInit()) {
                    visitObjectInit();
                }
                break;
            }
        }
        mv.visitInsn(opcode);
    }

    @Override
    public void visitTypeInsn(final int opcode, final String desc) {
        String name = desc.replace('/', '.');
        if (opcode == Opcodes.NEW) {
            visitAllocate(desc);
        }
        mv.visitTypeInsn(opcode, desc);
        if (opcode == Opcodes.ANEWARRAY) {
            String array_name = name.startsWith("[") ? "[" + name : "[L" + name + ";";
            visitAllocateArray(array_name);
        }
    }

    @Override
    public void visitIntInsn(final int opcode, final int operand) {
        mv.visitIntInsn(opcode, operand);
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
        mv.visitMultiANewArrayInsn(desc, dims);
        visitAllocateArray(desc);
    }

    @Override
    public void visitMethodInsn(final int opcode, final String owner, final String name, final String desc) {
        if (AProfRegistry.isInternalClass(context.getClassName())) {
            mv.visitMethodInsn(opcode, owner, name, desc);
            return;
        }

        // check if it is eligible object.clone call (that can get dispatched to actual Object.clone method
        boolean is_clone = opcode != Opcodes.INVOKESTATIC && name.equals(AProfTransformer.CLONE) && desc.equals(AProfTransformer.NOARG_RETURNS_OBJECT);
        boolean is_array_clone = is_clone && owner.startsWith("[");
        boolean is_object_clone = is_clone && AProfRegistry.isDirectCloneClass(owner.replace('/', '.'));

        String invoked_method = context.getLocationString(owner, name, desc);
        boolean is_method_tracked = AProfRegistry.isLocationTracked(invoked_method) && !context.getMethodName().startsWith(AProfTransformer.ACCESS_METHOD);

        if (is_method_tracked) {
            Label start = new Label();
            Label end = new Label();
            Label handler = new Label();
            Label done = new Label();
            visitMarkInvocationPoint();
            mv.visitTryCatchBlock(start, end, handler, null);
            mv.visitLabel(start);
            mv.visitMethodInsn(opcode, owner, name, desc);
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
            mv.visitMethodInsn(opcode, owner, name, desc);
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
                && (desc.equals(AProfTransformer.CLASS_INT_RETURNS_OBJECT) || desc.equals(AProfTransformer.CLASS_INT_ARR_RETURNS_OBJECT))) {
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
        mv.visitMaxs(maxStack, maxLocals);
    }

    private void pushAllocationPoint(String datatype) {
        datatype = datatype.replace('/', '.');
        mv.push(AProfRegistry.registerAllocationPoint(datatype, context.getLocation()));
    }

    /**
     * OPS implementation is chosen based on the class doing the allocation.
     *
     * @see com.devexperts.aprof.AProfOps#allocate(int)
     * @see com.devexperts.aprof.AProfOpsInternal#allocate(int)
     */
    private void visitAllocate(String desc) {
        if (context.getConfig().isLocation()) {
            pushAllocationPoint(desc);
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, context.getAprofOpsImplementation(), "allocate", AProfTransformer.INT_VOID);
        }
    }

    /**
     * @see com.devexperts.aprof.AProfOps#objectInit(Object)
     * @see com.devexperts.aprof.AProfOps#objectInitSize(Object)
     */
    private void visitObjectInit() {
        if (context.getConfig().isUnknown()) {
            mv.loadThis();
            if (context.getConfig().isSize()) {
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, AProfTransformer.APROF_OPS, "objectInitSize", AProfTransformer.OBJECT_VOID);
            } else {
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, AProfTransformer.APROF_OPS, "objectInit", AProfTransformer.OBJECT_VOID);
            }
        }
    }

    /**
     * @see com.devexperts.aprof.AProfOps#markInvokedMethod(int)
     */
    private void visitMarkInvokedMethod() {
        context.pushLocationStack(mv);
        mv.push(AProfRegistry.registerLocation(context.getLocation()));
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, AProfTransformer.LOCATION_STACK, "addInvokedMethod", AProfTransformer.INT_VOID);
    }

    /**
     * @see com.devexperts.aprof.AProfOps#unmarkInvokedMethod()
     */
    private void visitUnmarkInvokedMethod() {
        context.pushLocationStack(mv);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, AProfTransformer.LOCATION_STACK, "removeInvokedMethod", AProfTransformer.NOARG_VOID);
    }

    /**
     * @see com.devexperts.aprof.AProfOps#markInvocationPoint(int)
     */
    private void visitMarkInvocationPoint() {
        context.pushLocationStack(mv);
        mv.push(AProfRegistry.registerLocation(context.getLocation()));
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, AProfTransformer.LOCATION_STACK, "addInvocationPoint", AProfTransformer.INT_VOID);
    }

    /**
     * @see com.devexperts.aprof.AProfOps#unmarkInvocationPoint()
     */
    private void visitUnmarkInvocationPoint() {
        context.pushLocationStack(mv);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, AProfTransformer.LOCATION_STACK, "removeInvocationPoint", AProfTransformer.NOARG_VOID);
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
        if (context.getConfig().isArrays()) {
            if (AProfRegistry.isInternalClass(context.getClassName())) {
                return;
            }
            if (context.getConfig().isSize()) {
                mv.dup();
                pushAllocationPoint(desc);
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
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, context.getAprofOpsImplementation(), mname, sb.toString());
            } else {
                pushAllocationPoint(desc);
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, context.getAprofOpsImplementation(), "allocate", AProfTransformer.INT_VOID);
            }
        }
    }

    /**
     * @see com.devexperts.aprof.AProfOps#allocateReflect(Object, int)
     * @see com.devexperts.aprof.AProfOps#allocateReflectSize(Object, int)
     */
    private void visitAllocateReflect(String suffix) {
        assert !AProfRegistry.isInternalClass(context.getClassName());
        if (context.getConfig().isReflect()) {
            mv.dup();
            int loc = AProfRegistry.registerLocation(context.getLocation() + suffix);
            mv.push(loc);
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, AProfTransformer.APROF_OPS,
                    context.getConfig().isSize() ? "allocateReflectSize" : "allocateReflect",
                    AProfTransformer.OBJECT_INT_VOID);
        }
    }

    /**
     * @see com.devexperts.aprof.AProfOps#allocateReflectVClone(Object, int)
     * @see com.devexperts.aprof.AProfOps#allocateReflectVCloneSize(Object, int)
     */
    private void visitAllocateReflectVClone(String suffix) {
        assert !AProfRegistry.isInternalClass(context.getClassName());
        if (context.getConfig().isReflect()) {
            mv.dup();
            int loc = AProfRegistry.registerLocation(context.getLocation() + suffix);
            mv.push(loc);
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, AProfTransformer.APROF_OPS,
                    context.getConfig().isSize() ? "allocateReflectVCloneSize" : "allocateReflectVClone",
                    AProfTransformer.OBJECT_INT_VOID);
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
}
