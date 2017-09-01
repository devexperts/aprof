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
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;

/**
 * @author Dmitry Paraschenko
 */
abstract class AbstractMethodVisitor extends MethodVisitor {
	private static final String BOOLEAN_ARR_T_DESC = Type.getType(boolean[].class).getDescriptor();
	private static final String CHAR_ARR_T_DESC = Type.getType(char[].class).getDescriptor();
	private static final String FLOAT_ARR_T_DESC = Type.getType(float[].class).getDescriptor();
	private static final String DOUBLE_ARR_T_DESC = Type.getType(double[].class).getDescriptor();
	private static final String BYTE_ARR_T_DESC = Type.getType(byte[].class).getDescriptor();
	private static final String SHORT_ARR_T_DESC = Type.getType(short[].class).getDescriptor();
	private static final String INT_ARR_T_DESC = Type.getType(int[].class).getDescriptor();
	private static final String LONG_ARR_T_DESC = Type.getType(long[].class).getDescriptor();

	protected final GeneratorAdapter mv;
	protected final Context context;
	private final int classVersion;

	public AbstractMethodVisitor(GeneratorAdapter mv, Context context, int classVersion) {
		super(TransformerUtil.ASM_API, mv);
		this.mv = mv;
		this.context = context;
		this.classVersion = classVersion;
	}

	protected abstract void visitMarkDeclareLocationStack();

	protected abstract void visitStartInvokedMethod();

	protected abstract void visitReturnFromInvokedMethod();

	protected abstract void visitEndInvokedMethod();

	protected abstract void visitTrackedMethodInsn(int opcode, String owner, String name, String desc, boolean intf);

	protected abstract void visitObjectInit();

	protected abstract void visitAllocateBefore(String desc);

	protected abstract void visitAllocateAfter(String desc);

	protected abstract void visitAllocateArrayBefore(String desc);

	protected abstract void visitAllocateArrayAfter(String desc);

	protected abstract void visitAllocateArrayMulti(String desc);

	protected abstract void visitAllocateReflect(boolean cloneInvocation);

	protected abstract void visitAllocateReflectVClone();

	@Override
	public void visitCode() {
		mv.visitCode();
		visitMarkDeclareLocationStack();
		if (context.isMethodBodyTracked())
			visitStartInvokedMethod();
	}

	@Override
	public void visitInsn(int opcode) {
		switch (opcode) {
			case Opcodes.RETURN:
			case Opcodes.IRETURN:
			case Opcodes.FRETURN:
			case Opcodes.ARETURN:
			case Opcodes.LRETURN:
			case Opcodes.DRETURN:
				if (context.isObjectInit() && context.getConfig().isUnknown())
					visitObjectInit();
				if (context.isMethodBodyTracked())
					visitReturnFromInvokedMethod();
				break;
		}
		mv.visitInsn(opcode);
	}

	@Override
	public void visitMaxs(int maxStack, int maxLocals) {
		if (context.isMethodBodyTracked())
			visitEndInvokedMethod();
		mv.visitMaxs(maxStack, maxLocals);
	}

	@Override
	public void visitTypeInsn(final int opcode, final String desc) {
		switch (opcode) {
			case Opcodes.NEW:
				visitAllocateBefore(desc);
				mv.visitTypeInsn(opcode, desc);
				visitAllocateAfter(desc);
				break;
			case Opcodes.ANEWARRAY:
				if (!context.isIntrinsicArraysCopyOf()) {
					String arrayDesc = desc.startsWith("[") ? "[" + desc : "[L" + desc + ";";
					visitAllocateArrayBefore(arrayDesc);
					mv.visitTypeInsn(opcode, desc);
					visitAllocateArrayAfter(arrayDesc);
					break;
				}
				// ELSE -- FALLS THROUGH !!!
			default:
				mv.visitTypeInsn(opcode, desc);
		}
	}

	@Override
	public void visitIntInsn(final int opcode, final int operand) {
		if (opcode == Opcodes.NEWARRAY && !context.isIntrinsicArraysCopyOf()) {
			String arrayDesc;
			switch (operand) {
				case Opcodes.T_BOOLEAN:
					arrayDesc = BOOLEAN_ARR_T_DESC;
					break;
				case Opcodes.T_CHAR:
					arrayDesc = CHAR_ARR_T_DESC;
					break;
				case Opcodes.T_FLOAT:
					arrayDesc = FLOAT_ARR_T_DESC;
					break;
				case Opcodes.T_DOUBLE:
					arrayDesc = DOUBLE_ARR_T_DESC;
					break;
				case Opcodes.T_BYTE:
					arrayDesc = BYTE_ARR_T_DESC;
					break;
				case Opcodes.T_SHORT:
					arrayDesc = SHORT_ARR_T_DESC;
					break;
				case Opcodes.T_INT:
					arrayDesc = INT_ARR_T_DESC;
					break;
				case Opcodes.T_LONG:
					arrayDesc = LONG_ARR_T_DESC;
					break;
				default:
					assert false;  // should not happen
					return;
			}
			visitAllocateArrayBefore(arrayDesc);
			mv.visitIntInsn(opcode, operand);
			visitAllocateArrayAfter(arrayDesc);
		} else
			mv.visitIntInsn(opcode, operand);
	}

	@Override
	public void visitMultiANewArrayInsn(final String desc, final int dims) {
		mv.visitMultiANewArrayInsn(desc, dims);
		if (!context.isIntrinsicArraysCopyOf())
			visitAllocateArrayMulti(desc);
	}

	@Override
	public void visitMethodInsn(final int opcode, final String owner, final String name, final String desc, boolean intf) {
		if (context.isInternalLocation()) {
			// do not instrument method invocations in internal locations
			mv.visitMethodInsn(opcode, owner, name, desc, intf);
			return;
		}

		String cname = owner.replace('/', '.'); // convert owner to dot-separated class name

		// check if it is eligible object.clone call (that can get dispatched to actual Object.clone method
		boolean isClone = opcode != Opcodes.INVOKESTATIC && name.equals(TransformerUtil.CLONE) && desc.equals(TransformerUtil.NOARG_RETURNS_OBJECT);
		boolean isArrayClone = isClone && owner.startsWith("[");
		boolean isObjectClone = isClone && AProfRegistry.isDirectCloneClass(cname);

		if (context.isMethodInvocationTracked(cname, opcode, owner, name, desc, classVersion)) {
			visitTrackedMethodInsn(opcode, owner, name, desc, intf);
		} else {
			mv.visitMethodInsn(opcode, owner, name, desc, intf);
		}

		if (!context.getConfig().isReflect())
			return;
		if (context.isIntrinsicArraysCopyOf())
			return;

		if (opcode == Opcodes.INVOKEVIRTUAL && isObjectClone) {
			// INVOKEVIRTUAL needs runtime check of class that is being cloned
			visitAllocateReflectVClone();
			return;
		}

		if (opcode == Opcodes.INVOKESPECIAL && isObjectClone) {
			// Object.clone via super.clone (does not need runtime check)
			visitAllocateReflect(true);
			return;
		}

		if (isArrayClone) {
			// <array>.clone (usually via INVOKEVIRTUAL, but we don't care)
			visitAllocateReflect(false);
			return;
		}

		if (opcode == Opcodes.INVOKESTATIC && owner.equals("java/lang/reflect/Array") && name.equals("newInstance")
				&& (desc.equals(TransformerUtil.CLASS_INT_RETURNS_OBJECT) || desc.equals(TransformerUtil.CLASS_INT_ARR_RETURNS_OBJECT))) {
			// Array.newInstance
			visitAllocateReflect(false);
			return;
		}

		if (opcode == Opcodes.INVOKESTATIC && TransformerUtil.isIntrinsicArraysCopyOf(owner, name, desc)) {
			// HotSpot intrinsic for Arrays.copyOf and Arrays.copyOfRange
			visitAllocateReflect(false);
		}
	}
}
