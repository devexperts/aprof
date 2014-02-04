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

import org.objectweb.asm.commons.GeneratorAdapter;

/**
 * @author Dmitry Paraschenko
 */
class MethodAnalyzer extends AbstractMethodVisitor {
	public MethodAnalyzer(GeneratorAdapter mv, Context context) {
		super(mv, context);
	}

	@Override
	protected void visitMarkDeclareLocationStack() {
		// do nothing
	}

	public void requestLocationStack() {
		context.setLocationStackNeeded(true);
	}

	@Override
	protected void visitMarkInvokedMethod() {
		assert !context.isInternalLocation();
		requestLocationStack();
	}

	@Override
	protected void visitUnmarkInvokedMethod() {
		assert !context.isInternalLocation();
		requestLocationStack();
	}

	@Override
	protected void visitMarkInvocationPoint() {
		assert !context.isInternalLocation();
		requestLocationStack();
	}

	@Override
	protected void visitUnmarkInvocationPoint() {
		assert !context.isInternalLocation();
		requestLocationStack();
	}

	@Override
	protected void visitObjectInit() {
		// do nothing
	}

	@Override
	protected void visitAllocate(String desc) {
		requestLocationStack();
	}

	@Override
	protected void visitAllocateArray(String desc) {
		requestLocationStack();
	}

	@Override
	protected void visitAllocateReflect(String suffix) {
		requestLocationStack();
	}

	@Override
	protected void visitAllocateReflectVClone(String suffix) {
		requestLocationStack();
	}
}
