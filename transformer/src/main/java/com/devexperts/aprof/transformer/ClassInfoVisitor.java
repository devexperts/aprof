package com.devexperts.aprof.transformer;

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
