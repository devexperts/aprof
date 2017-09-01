package com.devexperts.aprof.transformer;

/*-
 * #%L
 * Aprof Transformer
 * %%
 * Copyright (C) 2002 - 2017 Devexperts, LLC
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */

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
	private static final boolean COUNT_ALLOCATION_AFTER = Boolean.getBoolean("com.devexperts.aprof.countAllocationAfter");

	private Label startFinally;

	public MethodTransformer(GeneratorAdapter mv, Context context, int classVersion) {
		super(mv, context, classVersion);
	}

	private void pushAllocationPoint(String desc) {
		mv.push(AProfRegistry.registerAllocationPoint(AProfRegistry.resolveClassName(desc), context.getLocation()));
	}

	private void pushLocationStack() {
		assert context.isLocationStackNeeded() : context;
		if (context.isInternalLocation()) {
			mv.visitInsn(Opcodes.ACONST_NULL);
			return;
		}
		int locationStack = context.getLocationStack();
		if (context.isMethodBodyTracked()) {
			assert locationStack >= 0 : context;
			mv.loadLocal(locationStack);
			return;
		}
		if (locationStack < 0) {
			mv.visitMethodInsn(Opcodes.INVOKESTATIC, TransformerUtil.LOCATION_STACK, "get", TransformerUtil.NOARG_RETURNS_STACK, false);
			return;
		}

		Label done = new Label();
		mv.loadLocal(locationStack);
		mv.dup();
		mv.ifNonNull(done);
		mv.pop();
		mv.visitMethodInsn(Opcodes.INVOKESTATIC, TransformerUtil.LOCATION_STACK, "get", TransformerUtil.NOARG_RETURNS_STACK, false);
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
			if (context.isMethodBodyTracked()) {
				mv.visitMethodInsn(Opcodes.INVOKESTATIC, TransformerUtil.LOCATION_STACK, "get", TransformerUtil.NOARG_RETURNS_STACK, false);
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
	protected void visitStartInvokedMethod() {
		assert !context.isInternalLocation() : context;
		startFinally = new Label();
		pushLocationStack();
		mv.push(AProfRegistry.registerLocation(context.getLocation()));
		mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, TransformerUtil.LOCATION_STACK, "addInvokedMethod", TransformerUtil.INT_VOID, false);
		mv.visitLabel(startFinally);
	}

	/**
	 * @see com.devexperts.aprof.LocationStack#removeInvokedMethod()
	 */
	@Override
	protected void visitReturnFromInvokedMethod() {
		assert !context.isInternalLocation() : context;
		pushLocationStack();
		mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, TransformerUtil.LOCATION_STACK, "removeInvokedMethod", TransformerUtil.NOARG_VOID, false);
	}

	@Override
	protected void visitEndInvokedMethod() {
		Label endFinally = new Label();
		mv.visitTryCatchBlock(startFinally, endFinally, endFinally, null);
		mv.visitLabel(endFinally);
		int var = mv.newLocal(Type.getType(Object.class));
		mv.storeLocal(var);
		visitReturnFromInvokedMethod();
		mv.loadLocal(var);
		mv.throwException();
	}

	private void visitMarkInvocationPoint() {
		pushLocationStack();
		mv.push(AProfRegistry.registerLocation(context.getLocation()));
		mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, TransformerUtil.LOCATION_STACK, "addInvocationPoint", TransformerUtil.INT_VOID, false);
	}

	private void visitUnmarkInvocationPoint() {
		pushLocationStack();
		mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, TransformerUtil.LOCATION_STACK, "removeInvocationPoint", TransformerUtil.NOARG_VOID, false);
	}

	@Override
	protected void visitTrackedMethodInsn(int opcode, String owner, String name, String desc, boolean intf) {
		assert !context.isInternalLocation() : context;
		Label start = new Label();
		Label end = new Label();
		Label handler = new Label();
		Label done = new Label();
		visitMarkInvocationPoint();
		mv.visitTryCatchBlock(start, end, handler, null);
		mv.visitLabel(start);
		mv.visitMethodInsn(opcode, owner, name, desc, intf);
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
	}

	/**
	 * @see com.devexperts.aprof.AProfOps#objectInit(Object)
	 * @see com.devexperts.aprof.AProfOps#objectInitSize(Object)
	 */
	@Override
	protected void visitObjectInit() {
		mv.loadThis();
		if (context.getConfig().isSize()) {
			mv.visitMethodInsn(Opcodes.INVOKESTATIC, TransformerUtil.APROF_OPS, "objectInitSize", TransformerUtil.OBJECT_VOID, false);
		} else {
			mv.visitMethodInsn(Opcodes.INVOKESTATIC, TransformerUtil.APROF_OPS, "objectInit", TransformerUtil.OBJECT_VOID, false);
		}
	}

	/**
	 * OPS implementation is chosen based on the class doing the allocation.
	 *
	 * @see com.devexperts.aprof.AProfOps#allocate(LocationStack, int)
	 * @see com.devexperts.aprof.AProfOps#allocateSize(LocationStack, int, Class)
	 */
	private void visitAllocate(String desc) {
		pushLocationStack();
		pushAllocationPoint(desc);
		if (context.getConfig().isSize()) {
			pushClass(desc);
			mv.visitMethodInsn(Opcodes.INVOKESTATIC, context.getAprofOpsImplementation(), "allocateSize", TransformerUtil.STACK_INT_CLASS_VOID, false);
		} else
			mv.visitMethodInsn(Opcodes.INVOKESTATIC, context.getAprofOpsImplementation(), "allocate", TransformerUtil.STACK_INT_VOID, false);
	}

	@Override
	protected void visitAllocateBefore(String desc) {
		if (!COUNT_ALLOCATION_AFTER)
			visitAllocate(desc);
	}

	@Override
	protected void visitAllocateAfter(String desc) {
		if (COUNT_ALLOCATION_AFTER)
			visitAllocate(desc);
	}

	/**
	 * OPS implementation is chosen based on the class doing the allocation.
	 */
	protected void visitAllocateArray(String desc) {
		if (context.getConfig().isSize()) {
			pushLocationStack();
			pushAllocationPoint(desc);
			Type type = Type.getType(desc);
			assert type.getSort() == Type.ARRAY;
			Type elementType = type.getElementType();
			String name = elementType.getSort() == Type.OBJECT || elementType.getSort() == Type.ARRAY ?
				"object" : elementType.getClassName();
			mv.visitMethodInsn(Opcodes.INVOKESTATIC, context.getAprofOpsImplementation(),
				name + "AllocateArraySize", TransformerUtil.INT_STACK_INT_VOID, false);
		} else {
			pushLocationStack();
			pushAllocationPoint(desc);
			mv.visitMethodInsn(Opcodes.INVOKESTATIC, context.getAprofOpsImplementation(),
				"allocate", TransformerUtil.STACK_INT_VOID, false);
		}
	}

	@Override
	protected void visitAllocateArrayBefore(String desc) {
		if (context.getConfig().isSize())
			mv.dup(); // keep array size to be allocated
		if (!COUNT_ALLOCATION_AFTER)
			visitAllocateArray(desc);
	}

	@Override
	protected void visitAllocateArrayAfter(String desc) {
		if (COUNT_ALLOCATION_AFTER) {
			if (context.getConfig().isSize())
				mv.swap(); // retrieve array size from stack
			visitAllocateArray(desc);
		}
	}

	@Override
	protected void visitAllocateArrayMulti(String desc) {
		if (context.getConfig().isSize()) {
			mv.dup();
			pushLocationStack();
			pushAllocationPoint(desc);
			mv.visitMethodInsn(Opcodes.INVOKESTATIC, context.getAprofOpsImplementation(),
				"allocateArraySizeMulti", TransformerUtil.OBJECT_ARR_STACK_INT_VOID, false);
		} else {
			pushLocationStack();
			pushAllocationPoint(desc);
			mv.visitMethodInsn(Opcodes.INVOKESTATIC, context.getAprofOpsImplementation(),
				"allocate", TransformerUtil.STACK_INT_VOID, false);
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
				TransformerUtil.OBJECT_STACK_INT_VOID, false);
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
				TransformerUtil.OBJECT_STACK_INT_VOID, false);
	}
}
