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

import static org.objectweb.asm.Opcodes.*;

/**
 * @author Dmitry Paraschenko
 */
abstract class AbstractMethodVisitor extends MethodAdapter {
	private static final Type BOOLEAN_ARR_T = Type.getType(boolean[].class);
	private static final Type CHAR_ARR_T = Type.getType(char[].class);
	private static final Type FLOAT_ARR_T = Type.getType(float[].class);
	private static final Type DOUBLE_ARR_T = Type.getType(double[].class);
	private static final Type BYTE_ARR_T = Type.getType(byte[].class);
	private static final Type SHORT_ARR_T = Type.getType(short[].class);
	private static final Type INT_ARR_T = Type.getType(int[].class);
	private static final Type LONG_ARR_T = Type.getType(long[].class);

	protected final GeneratorAdapter mv;
	protected final Context context;

	public AbstractMethodVisitor(GeneratorAdapter mv, Context context) {
		super(mv);
		this.mv = mv;
		this.context = context;
	}

	protected abstract void visitMarkDeclareLocationStack();

	protected abstract void visitMarkInvokedMethod();

	protected abstract void visitUnmarkInvokedMethod();

	protected abstract void visitMarkInvocationPoint();

	protected abstract void visitUnmarkInvocationPoint();

	protected abstract void visitObjectInit();

	protected abstract void visitAllocate(String desc);

	protected abstract void visitAllocateArray(String array_name);

	protected abstract void visitAllocateReflect(String arrayNewinstanceSuffix);

	protected abstract void visitAllocateReflectVClone(String cloneSuffix);

	@Override
	public void visitCode() {
		mv.visitCode();
		visitMarkDeclareLocationStack();
		if (context.isMethodTracked()) {
			visitMarkInvokedMethod();
		}
	}

	@Override
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
				if (context.isObjectInit() && context.getConfig().isUnknown()) {
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
		if (opcode == Opcodes.NEW && context.getConfig().isLocation()) {
			visitAllocate(desc);
		}
		mv.visitTypeInsn(opcode, desc);
		if (opcode == Opcodes.ANEWARRAY && context.getConfig().isArrays()) {
			String array_name = name.startsWith("[") ? "[" + name : "[L" + name + ";";
			visitAllocateArray(array_name);
		}
	}

	@Override
	public void visitIntInsn(final int opcode, final int operand) {
		mv.visitIntInsn(opcode, operand);
		if (opcode == Opcodes.NEWARRAY && context.getConfig().isArrays()) {
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
		if (context.getConfig().isArrays()) {
			visitAllocateArray(desc);
		}
	}

	@Override
	public void visitMethodInsn(final int opcode, final String owner, final String name, final String desc) {
		if (AProfRegistry.isInternalLocation(context.getClassName())) {
			mv.visitMethodInsn(opcode, owner, name, desc);
			return;
		}

		// check if it is eligible object.clone call (that can get dispatched to actual Object.clone method
		boolean is_clone = opcode != Opcodes.INVOKESTATIC && name.equals(AProfTransformer.CLONE) && desc.equals(AProfTransformer.NOARG_RETURNS_OBJECT);
		boolean is_array_clone = is_clone && owner.startsWith("[");
		boolean is_object_clone = is_clone && AProfRegistry.isDirectCloneClass(owner.replace('/', '.'));

		String invoked_method = context.getLocationString(owner, name, desc);
		boolean is_method_tracked = context.isLocationTracked(invoked_method) && !context.getMethodName().startsWith(AProfTransformer.ACCESS_METHOD);

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

		if (!context.getConfig().isReflect()) {
			return;
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
}
