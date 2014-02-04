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

import com.devexperts.aprof.AProfRegistry;
import com.devexperts.aprof.LocationStack;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.GeneratorAdapter;

/**
 * @author Dmitry Paraschenko
 */
class MethodTransformer extends AbstractMethodVisitor {
	public MethodTransformer(GeneratorAdapter mv, Context context) {
		super(mv, context);
	}

	protected void pushAllocationPoint(String desc) {
		String cname = desc.replace('/', '.');
		mv.push(AProfRegistry.registerAllocationPoint(cname, context.getLocation()));
	}

	public void pushLocationStack() {
		assert context.isLocationStackNeeded();
		if (context.isInternalLocation()) {
			mv.visitInsn(Opcodes.ACONST_NULL);
			return;
		}
		int locationStack = context.getLocationStack();
		if (context.isMethodTracked()) {
			assert locationStack >= 0;
			mv.loadLocal(locationStack);
			return;
		}
		if (locationStack < 0) {
			mv.visitMethodInsn(Opcodes.INVOKESTATIC, AProfTransformer.LOCATION_STACK, "get", AProfTransformer.NOARG_RETURNS_STACK);
			return;
		}

		Label done = new Label();
		mv.loadLocal(locationStack);
		mv.dup();
		mv.ifNonNull(done);
		mv.pop();
		mv.visitMethodInsn(Opcodes.INVOKESTATIC, AProfTransformer.LOCATION_STACK, "get", AProfTransformer.NOARG_RETURNS_STACK);
		mv.dup();
		mv.storeLocal(locationStack);
		mv.visitLabel(done);
	}

	private void pushClass(String desc) {
		mv.visitLdcInsn(Type.getObjectType(desc));
	}

	@Override
	protected void visitMarkDeclareLocationStack() {
		if (context.isLocationStackNeeded()) {
			int locationStack = mv.newLocal(Type.getType(LocationStack.class));
			if (context.isMethodTracked()) {
				mv.visitMethodInsn(Opcodes.INVOKESTATIC, AProfTransformer.LOCATION_STACK, "get", AProfTransformer.NOARG_RETURNS_STACK);
			} else {
				mv.visitInsn(Opcodes.ACONST_NULL);
			}
			mv.storeLocal(locationStack);
			context.setLocationStack(locationStack);
		}
	}

	/**
	 * @see com.devexperts.aprof.LocationStack#addInvokedMethod(int)
	 */
	@Override
	protected void visitMarkInvokedMethod() {
		assert !context.isInternalLocation();
		pushLocationStack();
		mv.push(AProfRegistry.registerLocation(context.getLocation()));
		mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, AProfTransformer.LOCATION_STACK, "addInvokedMethod", AProfTransformer.INT_VOID);
	}

	/**
	 * @see com.devexperts.aprof.LocationStack#removeInvokedMethod()
	 */
	@Override
	protected void visitUnmarkInvokedMethod() {
		assert !context.isInternalLocation();
		pushLocationStack();
		mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, AProfTransformer.LOCATION_STACK, "removeInvokedMethod", AProfTransformer.NOARG_VOID);
	}

	/**
	 * @see com.devexperts.aprof.LocationStack#addInvocationPoint(int)
	 */
	@Override
	protected void visitMarkInvocationPoint() {
		assert !context.isInternalLocation();
		pushLocationStack();
		mv.push(AProfRegistry.registerLocation(context.getLocation()));
		mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, AProfTransformer.LOCATION_STACK, "addInvocationPoint", AProfTransformer.INT_VOID);
	}

	/**
	 * @see com.devexperts.aprof.LocationStack#removeInvocationPoint()
	 */
	@Override
	protected void visitUnmarkInvocationPoint() {
		assert !context.isInternalLocation();
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
		if (context.getConfig().isSize()) {
			pushClass(desc);
			mv.visitMethodInsn(Opcodes.INVOKESTATIC, context.getAprofOpsImplementation(), "allocateSize", AProfTransformer.STACK_INT_CLASS_VOID);
		} else
			mv.visitMethodInsn(Opcodes.INVOKESTATIC, context.getAprofOpsImplementation(), "allocate", AProfTransformer.STACK_INT_VOID);
	}

	/**
	 * OPS implementation is chosen based on the class doing the allocation.
	 *
	 * @see com.devexperts.aprof.AProfOps#allocate(int)
	 * @see com.devexperts.aprof.AProfOps#allocateArraySize(boolean[], LocationStack, int)
	 * @see com.devexperts.aprof.AProfOps#allocateArraySize(byte[], LocationStack, int)
	 * @see com.devexperts.aprof.AProfOps#allocateArraySize(char[], LocationStack, int)
	 * @see com.devexperts.aprof.AProfOps#allocateArraySize(short[], LocationStack, int)
	 * @see com.devexperts.aprof.AProfOps#allocateArraySize(int[], LocationStack, int)
	 * @see com.devexperts.aprof.AProfOps#allocateArraySize(long[], LocationStack, int)
	 * @see com.devexperts.aprof.AProfOps#allocateArraySize(float[], LocationStack, int)
	 * @see com.devexperts.aprof.AProfOps#allocateArraySize(double[], LocationStack, int)
	 * @see com.devexperts.aprof.AProfOps#allocateArraySize(Object[], LocationStack, int)
	 * @see com.devexperts.aprof.AProfOps#allocateArraySizeMulti(Object[], LocationStack, int)
	 * @see com.devexperts.aprof.AProfOpsInternal#allocate(int)
	 * @see com.devexperts.aprof.AProfOpsInternal#allocateArraySize(boolean[], LocationStack, int)
	 * @see com.devexperts.aprof.AProfOpsInternal#allocateArraySize(byte[], LocationStack, int)
	 * @see com.devexperts.aprof.AProfOpsInternal#allocateArraySize(char[], LocationStack, int)
	 * @see com.devexperts.aprof.AProfOpsInternal#allocateArraySize(short[], LocationStack, int)
	 * @see com.devexperts.aprof.AProfOpsInternal#allocateArraySize(int[], LocationStack, int)
	 * @see com.devexperts.aprof.AProfOpsInternal#allocateArraySize(long[], LocationStack, int)
	 * @see com.devexperts.aprof.AProfOpsInternal#allocateArraySize(float[], LocationStack, int)
	 * @see com.devexperts.aprof.AProfOpsInternal#allocateArraySize(double[], LocationStack, int)
	 * @see com.devexperts.aprof.AProfOpsInternal#allocateArraySize(Object[], LocationStack, int)
	 * @see com.devexperts.aprof.AProfOpsInternal#allocateArraySizeMulti(Object[], LocationStack, int)
	 * @param desc
	 */
	@Override
	protected void visitAllocateArray(String desc) {
		assert context.getConfig().isArrays();
		if (context.getConfig().isSize()) {
			mv.dup();
			pushLocationStack();
			pushAllocationPoint(desc);
			boolean isMulti = desc.lastIndexOf('[') > 0;
			boolean isPrimitive = desc.length() == 2;
			StringBuilder sb = new StringBuilder();
			sb.append("(");
			if (isPrimitive) {
				sb.append(desc);
			} else {
				sb.append("[Ljava/lang/Object;");
			}
			sb.append("Lcom/devexperts/aprof/LocationStack;I)V");
			String mname = isMulti ? "allocateArraySizeMulti" : "allocateArraySize";
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
		assert !AProfRegistry.isInternalLocationClass(context.getLocationClass());
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
		assert !AProfRegistry.isInternalLocationClass(context.getLocationClass());
		assert context.getConfig().isReflect();
		mv.dup();
		mv.push(AProfRegistry.registerLocation(context.getLocation() + suffix));
		mv.visitMethodInsn(Opcodes.INVOKESTATIC, AProfTransformer.APROF_OPS,
				context.getConfig().isSize() ? "allocateReflectVCloneSize" : "allocateReflectVClone",
				AProfTransformer.OBJECT_INT_VOID);
	}
}
