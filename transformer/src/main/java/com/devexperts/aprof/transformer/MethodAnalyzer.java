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

import org.objectweb.asm.commons.GeneratorAdapter;

/**
 * @author Dmitry Paraschenko
 */
class MethodAnalyzer extends AbstractMethodVisitor {
	public MethodAnalyzer(GeneratorAdapter mv, Context context, int classVersion) {
		super(mv, context, classVersion);
	}

	@Override
	protected void visitMarkDeclareLocationStack() {
		// do nothing
	}

	private void requestTransformation() {
		context.setTransformationNeeded(true);
	}

	private void requestLocationStack() {
		requestTransformation();
		context.setLocationStackNeeded(true);
	}

	@Override
	protected void visitStartInvokedMethod() {
		assert !context.isInternalLocation();
		requestLocationStack();
	}

	@Override
	protected void visitReturnFromInvokedMethod() {
		assert !context.isInternalLocation();
		requestLocationStack();
	}

	@Override
	protected void visitEndInvokedMethod() {
		assert !context.isInternalLocation();
		requestLocationStack();
	}

	protected void visitTrackedMethodInsn(int opcode, String owner, String name, String desc, boolean intf) {
		assert !context.isInternalLocation();
		requestLocationStack();
		mv.visitMethodInsn(opcode, owner, name, desc, intf);
	}

	@Override
	protected void visitObjectInit() {
		requestTransformation();
	}

	@Override
	protected void visitAllocateBefore(String desc) {
		requestLocationStack();
	}

	@Override
	protected void visitAllocateAfter(String desc) {}

	@Override
	protected void visitAllocateArrayBefore(String desc) {
		requestLocationStack();
	}

	@Override
	protected void visitAllocateArrayAfter(String desc) {}

	@Override
	protected void visitAllocateArrayMulti(String desc) {
		requestLocationStack();
	}

	@Override
	protected void visitAllocateReflect(boolean cloneInvocation) {
		requestLocationStack();
	}

	@Override
	protected void visitAllocateReflectVClone() {
		requestLocationStack();
	}
}
