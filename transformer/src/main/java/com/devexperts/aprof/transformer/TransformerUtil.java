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

import org.objectweb.asm.Opcodes;

class TransformerUtil {
	private TransformerUtil() {} // do not create

	static final int MAJOR_VERSION_MASK = 0xffff;
	static final int ASM_API = Opcodes.ASM5;
	static final int MIN_CLASS_VERSION = Opcodes.V1_5; // needed to support ldc <class> and avoid "Illegal type in constant pool"

	static final String APROF_OPS = "com/devexperts/aprof/AProfOps";
	static final String APROF_OPS_INTERNAL = "com/devexperts/aprof/AProfOpsInternal";

	static final String LOCATION_STACK = "com/devexperts/aprof/LocationStack";

	static final String OBJECT = "java/lang/Object";
	static final String OBJECT_CLASS_NAME = "java.lang.Object";

	static final String ACCESS_METHOD = "access$";
	static final String INIT = "<init>";
	static final String CLONE = "clone";

	static final String NOARG_RETURNS_OBJECT = "()Ljava/lang/Object;";
	static final String NOARG_RETURNS_STACK = "()Lcom/devexperts/aprof/LocationStack;";
	static final String NOARG_VOID = "()V";
	static final String INT_VOID = "(I)V";
	static final String STACK_INT_VOID = "(Lcom/devexperts/aprof/LocationStack;I)V";
	static final String INT_STACK_INT_VOID = "(ILcom/devexperts/aprof/LocationStack;I)V";
	static final String OBJECT_ARR_STACK_INT_VOID = "([Ljava/lang/Object;Lcom/devexperts/aprof/LocationStack;I)V";
	static final String STACK_INT_CLASS_VOID = "(Lcom/devexperts/aprof/LocationStack;ILjava/lang/Class;)V";
	static final String OBJECT_VOID = "(Ljava/lang/Object;)V";
	static final String OBJECT_STACK_INT_VOID = "(Ljava/lang/Object;Lcom/devexperts/aprof/LocationStack;I)V";
	static final String CLASS_INT_RETURNS_OBJECT = "(Ljava/lang/Class;I)Ljava/lang/Object;";
	static final String CLASS_INT_ARR_RETURNS_OBJECT = "(Ljava/lang/Class;[I)Ljava/lang/Object;";

	static boolean isIntrinsicArraysCopyOf(String owner, String mname, String desc) {
		return owner.equals("java/util/Arrays") &&
			((mname.equals("copyOf") && desc.equals("([Ljava/lang/Object;ILjava/lang/Class;)[Ljava/lang/Object;"))
			|| (mname.equals("copyOfRange") && desc.equals("([Ljava/lang/Object;IILjava/lang/Class;)[Ljava/lang/Object;")));
	}

	static void describeClassLoaderForLog(StringBuilder sb, ClassLoader loader) {
		if (loader != null) {
			sb.append(" [in ");
			sb.append(loader.getClass().getName());
			sb.append('@');
			sb.append(System.identityHashCode(loader));
			sb.append("]");
		}
	}
}
