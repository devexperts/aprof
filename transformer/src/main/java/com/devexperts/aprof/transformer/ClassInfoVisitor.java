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

import java.util.*;

import org.objectweb.asm.*;

/**
* @author Roman Elizarov
*/
class ClassInfoVisitor extends ClassVisitor {
	private final Map<String, Set<String>> virtualMethods;
	ClassInfo result;

	public ClassInfoVisitor(boolean loadVirtualMethods) {
		super(TransformerUtil.ASM_API);
		this.virtualMethods = loadVirtualMethods ? new HashMap<String, Set<String>>() : null;
	}

	@Override
	public void visit(int version, int access, String name, String signature, String superName,
		String[] interfaces)
	{
		result = new ClassInfo(access, name, superName, interfaces);
		result.setVirtualMethods(virtualMethods);
	}

	@Override
	public MethodVisitor visitMethod(int access, String name, String desc, String signature,
		String[] exceptions)
	{
		if (virtualMethods != null &&
			(access & Opcodes.ACC_STATIC) == 0 &&
			(access & Opcodes.ACC_PRIVATE) == 0 &&
			(access & Opcodes.ACC_FINAL) == 0 &&
			!name.equals(TransformerUtil.INIT))
		{
			Set<String> descSet = virtualMethods.get(name);
			if (descSet == null)
				virtualMethods.put(name, descSet = new HashSet<String>());
			descSet.add(desc);
		}
		return null;
	}
}
