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
import com.devexperts.aprof.LocationStack;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;

/**
 * @author Dmitry Paraschenko
 */
class MethodTransformer extends AbstractMethodVisitor {
	public MethodTransformer(GeneratorAdapter mv, Context context) {
		super(mv, context);
	}

	protected void pushAllocationPoint(String datatype) {
		datatype = datatype.replace('/', '.');
		mv.push(AProfRegistry.registerAllocationPoint(datatype, context.getLocation()));
	}

	public void pushLocationStack() {
		assert context.isLocationStackNeeded();
		int location_stack = context.getLocationStack();
		if (context.isMethodTracked()) {
			assert location_stack >= 0;
			mv.loadLocal(location_stack);
			return;
		}
		if (location_stack < 0) {
			mv.visitMethodInsn(Opcodes.INVOKESTATIC, AProfTransformer.LOCATION_STACK, "get", AProfTransformer.NOARG_RETURNS_STACK);
			return;
		}

		Label done = new Label();
		mv.loadLocal(location_stack);
		mv.dup();
		mv.ifNonNull(done);
		mv.pop();
		mv.visitMethodInsn(Opcodes.INVOKESTATIC, AProfTransformer.LOCATION_STACK, "get", AProfTransformer.NOARG_RETURNS_STACK);
		mv.dup();
		mv.storeLocal(location_stack);
		mv.visitLabel(done);
	}

	@Override
	protected void visitMarkDeclareLocationStack() {
		if (context.isLocationStackNeeded()) {
			int location_stack = mv.newLocal(Type.getType(LocationStack.class));
			if (context.isMethodTracked()) {
				mv.visitMethodInsn(Opcodes.INVOKESTATIC, AProfTransformer.LOCATION_STACK, "get", AProfTransformer.NOARG_RETURNS_STACK);
			} else {
				mv.visitInsn(Opcodes.ACONST_NULL);
			}
			mv.storeLocal(location_stack);
			context.setLocationStack(location_stack);
		}
	}

	/**
	 * @see com.devexperts.aprof.LocationStack#addInvokedMethod(int)
	 */
	@Override
	protected void visitMarkInvokedMethod() {
		pushLocationStack();
		mv.push(AProfRegistry.registerLocation(context.getLocation()));
		mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, AProfTransformer.LOCATION_STACK, "addInvokedMethod", AProfTransformer.INT_VOID);
	}

	/**
	 * @see com.devexperts.aprof.LocationStack#removeInvokedMethod()
	 */
	@Override
	protected void visitUnmarkInvokedMethod() {
		pushLocationStack();
		mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, AProfTransformer.LOCATION_STACK, "removeInvokedMethod", AProfTransformer.NOARG_VOID);
	}

	/**
	 * @see com.devexperts.aprof.LocationStack#addInvocationPoint(int)
	 */
	@Override
	protected void visitMarkInvocationPoint() {
		pushLocationStack();
		mv.push(AProfRegistry.registerLocation(context.getLocation()));
		mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, AProfTransformer.LOCATION_STACK, "addInvocationPoint", AProfTransformer.INT_VOID);
	}

	/**
	 * @see com.devexperts.aprof.LocationStack#removeInvocationPoint()
	 */
	@Override
	protected void visitUnmarkInvocationPoint() {
		pushLocationStack();
		mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, AProfTransformer.LOCATION_STACK, "removeInvocationPoint", AProfTransformer.NOARG_VOID);
	}

	/**
	 * @see com.devexperts.aprof.AProfOps#objectInit(Object)
	 * @see com.devexperts.aprof.AProfOps#objectInitSize(Object)
	 */
	@Override
	protected void visitObjectInit() {
		mv.loadThis();
		if (context.getConfig().isSize()) {
			mv.visitMethodInsn(Opcodes.INVOKESTATIC, AProfTransformer.APROF_OPS, "objectInitSize", AProfTransformer.OBJECT_VOID);
		} else {
			mv.visitMethodInsn(Opcodes.INVOKESTATIC, AProfTransformer.APROF_OPS, "objectInit", AProfTransformer.OBJECT_VOID);
		}
	}

	/**
	 * OPS implementation is chosen based on the class doing the allocation.
	 *
	 * @see com.devexperts.aprof.AProfOps#allocate(int)
	 * @see com.devexperts.aprof.AProfOpsInternal#allocate(int)
	 */
	@Override
	protected void visitAllocate(String desc) {
		assert context.getConfig().isLocation();
		pushLocationStack();
		pushAllocationPoint(desc);
		mv.visitMethodInsn(Opcodes.INVOKESTATIC, context.getAprofOpsImplementation(), "allocate", AProfTransformer.STACK_INT_VOID);
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
	@Override
	protected void visitAllocateArray(String desc) {
		assert context.getConfig().isArrays();
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
			pushLocationStack();
			pushAllocationPoint(desc);
			mv.visitMethodInsn(Opcodes.INVOKESTATIC, context.getAprofOpsImplementation(), "allocate", AProfTransformer.STACK_INT_VOID);
		}
	}

	/**
	 * @see com.devexperts.aprof.AProfOps#allocateReflect(Object, int)
	 * @see com.devexperts.aprof.AProfOps#allocateReflectSize(Object, int)
	 */
	@Override
	protected void visitAllocateReflect(String suffix) {
		assert !Context.isInternalLocation(context.getClassName());
		assert context.getConfig().isReflect();
		mv.dup();
		int loc = AProfRegistry.registerLocation(context.getLocation() + suffix);
		mv.push(loc);
		mv.visitMethodInsn(Opcodes.INVOKESTATIC, AProfTransformer.APROF_OPS,
				context.getConfig().isSize() ? "allocateReflectSize" : "allocateReflect",
				AProfTransformer.OBJECT_INT_VOID);
	}

	/**
	 * @see com.devexperts.aprof.AProfOps#allocateReflectVClone(Object, int)
	 * @see com.devexperts.aprof.AProfOps#allocateReflectVCloneSize(Object, int)
	 */
	@Override
	protected void visitAllocateReflectVClone(String suffix) {
		assert !Context.isInternalLocation(context.getClassName());
		assert context.getConfig().isReflect();
		mv.dup();
		int loc = AProfRegistry.registerLocation(context.getLocation() + suffix);
		mv.push(loc);
		mv.visitMethodInsn(Opcodes.INVOKESTATIC, AProfTransformer.APROF_OPS,
				context.getConfig().isSize() ? "allocateReflectVCloneSize" : "allocateReflectVClone",
				AProfTransformer.OBJECT_INT_VOID);
	}
}
