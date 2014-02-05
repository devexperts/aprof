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
		assert context.isLocationStackNeeded() : context;
		if (context.isInternalLocation()) {
			mv.visitInsn(Opcodes.ACONST_NULL);
			return;
		}
		int locationStack = context.getLocationStack();
		if (context.isMethodTracked()) {
			assert locationStack >= 0 : context;
			mv.loadLocal(locationStack);
			return;
		}
		if (locationStack < 0) {
			mv.visitMethodInsn(Opcodes.INVOKESTATIC, TransformerUtil.LOCATION_STACK, "get", TransformerUtil.NOARG_RETURNS_STACK);
			return;
		}

		Label done = new Label();
		mv.loadLocal(locationStack);
		mv.dup();
		mv.ifNonNull(done);
		mv.pop();
		mv.visitMethodInsn(Opcodes.INVOKESTATIC, TransformerUtil.LOCATION_STACK, "get", TransformerUtil.NOARG_RETURNS_STACK);
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
				mv.visitMethodInsn(Opcodes.INVOKESTATIC, TransformerUtil.LOCATION_STACK, "get", TransformerUtil.NOARG_RETURNS_STACK);
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
		assert !context.isInternalLocation() : context;
		pushLocationStack();
		mv.push(AProfRegistry.registerLocation(context.getLocation()));
		mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, TransformerUtil.LOCATION_STACK, "addInvokedMethod", TransformerUtil.INT_VOID);
	}

	/**
	 * @see com.devexperts.aprof.LocationStack#removeInvokedMethod()
	 */
	@Override
	protected void visitUnmarkInvokedMethod() {
		assert !context.isInternalLocation() : context;
		pushLocationStack();
		mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, TransformerUtil.LOCATION_STACK, "removeInvokedMethod", TransformerUtil.NOARG_VOID);
	}

	/**
	 * @see com.devexperts.aprof.LocationStack#addInvocationPoint(int)
	 */
	@Override
	protected void visitMarkInvocationPoint() {
		assert !context.isInternalLocation() : context;
		pushLocationStack();
		mv.push(AProfRegistry.registerLocation(context.getLocation()));
		mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, TransformerUtil.LOCATION_STACK, "addInvocationPoint", TransformerUtil.INT_VOID);
	}

	/**
	 * @see com.devexperts.aprof.LocationStack#removeInvocationPoint()
	 */
	@Override
	protected void visitUnmarkInvocationPoint() {
		assert !context.isInternalLocation() : context;
		pushLocationStack();
		mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, TransformerUtil.LOCATION_STACK, "removeInvocationPoint", TransformerUtil.NOARG_VOID);
	}

	/**
	 * @see com.devexperts.aprof.AProfOps#objectInit(Object)
	 * @see com.devexperts.aprof.AProfOps#objectInitSize(Object)
	 */
	@Override
	protected void visitObjectInit() {
		mv.loadThis();
		if (context.getConfig().isSize()) {
			mv.visitMethodInsn(Opcodes.INVOKESTATIC, TransformerUtil.APROF_OPS, "objectInitSize", TransformerUtil.OBJECT_VOID);
		} else {
			mv.visitMethodInsn(Opcodes.INVOKESTATIC, TransformerUtil.APROF_OPS, "objectInit", TransformerUtil.OBJECT_VOID);
		}
	}

	/**
	 * OPS implementation is chosen based on the class doing the allocation.
	 *
	 * @see com.devexperts.aprof.AProfOps#allocate(LocationStack, int)
	 * @see com.devexperts.aprof.AProfOps#allocateSize(LocationStack, int, Class)
	 */
	@Override
	protected void visitAllocate(String desc) {
		assert context.getConfig().isLocation() : context;
		pushLocationStack();
		pushAllocationPoint(desc);
		if (context.getConfig().isSize()) {
			pushClass(desc);
			mv.visitMethodInsn(Opcodes.INVOKESTATIC, context.getAprofOpsImplementation(), "allocateSize", TransformerUtil.STACK_INT_CLASS_VOID);
		} else
			mv.visitMethodInsn(Opcodes.INVOKESTATIC, context.getAprofOpsImplementation(), "allocate", TransformerUtil.STACK_INT_VOID);
	}

	/**
	 * OPS implementation is chosen based on the class doing the allocation.
	 */
	@Override
	protected void visitAllocateArray(String desc) {
		assert context.getConfig().isArrays() : context;
		if (context.getConfig().isSize()) {
			mv.dup();
			pushLocationStack();
			pushAllocationPoint(desc);
			Type type = Type.getType(desc);
			assert type.getSort() == Type.ARRAY;
			Type elementType = type.getElementType();
			String name = elementType.getSort() == Type.OBJECT || elementType.getSort() == Type.ARRAY ?
				"object" : elementType.getClassName();
			mv.visitMethodInsn(Opcodes.INVOKESTATIC, context.getAprofOpsImplementation(),
				name + "AllocateArraySize", TransformerUtil.INT_STACK_INT_VOID);
		} else {
			pushLocationStack();
			pushAllocationPoint(desc);
			mv.visitMethodInsn(Opcodes.INVOKESTATIC, context.getAprofOpsImplementation(),
				"allocate", TransformerUtil.STACK_INT_VOID);
		}
	}

	@Override
	protected void visitAllocateArrayMulti(String desc) {
		if (context.getConfig().isSize()) {
			mv.dup();
			pushLocationStack();
			pushAllocationPoint(desc);
			mv.visitMethodInsn(Opcodes.INVOKESTATIC, context.getAprofOpsImplementation(),
				"allocateArraySizeMulti", TransformerUtil.OBJECT_ARR_STACK_INT_VOID);
		} else {
			pushLocationStack();
			pushAllocationPoint(desc);
			mv.visitMethodInsn(Opcodes.INVOKESTATIC, context.getAprofOpsImplementation(),
				"allocate", TransformerUtil.STACK_INT_VOID);
		}
	}

	/**
	 * @see com.devexperts.aprof.AProfOps#allocateReflect(Object, LocationStack, int)
	 * @see com.devexperts.aprof.AProfOps#allocateReflectSize(Object, LocationStack, int)
	 */
	@Override
	protected void visitAllocateReflect(boolean objectCloneInvocation) {
		assert !context.isInternalLocation() : context;
		assert context.getConfig().isReflect() : context;
		mv.dup();
		pushLocationStack();
		mv.push(AProfRegistry.registerLocation(context.getLocation(), objectCloneInvocation));
		mv.visitMethodInsn(Opcodes.INVOKESTATIC, context.getAprofOpsImplementation(),
				context.getConfig().isSize() ? "allocateReflectSize" : "allocateReflect",
				TransformerUtil.OBJECT_STACK_INT_VOID);
	}

	/**
	 * @see com.devexperts.aprof.AProfOps#allocateReflectVClone(Object, LocationStack, int)
	 * @see com.devexperts.aprof.AProfOps#allocateReflectVCloneSize(Object, LocationStack, int)
	 */
	@Override
	protected void visitAllocateReflectVClone() {
		assert !context.isInternalLocation() : context;
		assert context.getConfig().isReflect() : context;
		mv.dup();
		pushLocationStack();
		mv.push(AProfRegistry.registerLocation(context.getLocation(), true));
		mv.visitMethodInsn(Opcodes.INVOKESTATIC, context.getAprofOpsImplementation(),
				context.getConfig().isSize() ? "allocateReflectVCloneSize" : "allocateReflectVClone",
				TransformerUtil.OBJECT_STACK_INT_VOID);
	}
}
