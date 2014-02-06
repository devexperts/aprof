package com.devexperts.aprof;

import java.lang.instrument.ClassFileTransformer;

/**
 * Class transformer and analyzer. It is loaded in a separate class-loader to avoid ASM library version
 * clashes with the target code and so it is referred to by this interface.
 *
 * @author Roman Elizarov
 */
public interface TransformerAnalyzer extends ClassFileTransformer {
	/**
	 * Returns class hierarchy or null if it cannot be loaded.
	 */
	public ClassHierarchy getClassHierarchy(String className, ClassLoader loader);
}
