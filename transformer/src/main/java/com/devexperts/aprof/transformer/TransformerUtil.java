package com.devexperts.aprof.transformer;

import org.objectweb.asm.Opcodes;

class TransformerUtil {
	private TransformerUtil() {} // do not create

	static final int MIN_CLASS_VERSION = Opcodes.V1_5; // needed to support ldc <class> and avoid "Illegal type in constant pool"

	static final String APROF_OPS = "com/devexperts/aprof/AProfOps";
	static final String APROF_OPS_INTERNAL = "com/devexperts/aprof/AProfOpsInternal";

	static final String LOCATION_STACK = "com/devexperts/aprof/LocationStack";

	static final String OBJECT_CLASS_NAME = "java.lang.Object";

	static final String ACCESS_METHOD = "access$";
	static final String OBJECT_INIT = "<init>";
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
}
