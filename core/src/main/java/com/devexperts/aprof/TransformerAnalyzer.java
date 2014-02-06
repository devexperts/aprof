package com.devexperts.aprof;

import java.lang.instrument.ClassFileTransformer;
import java.util.List;

/**
 * Class transformer and analyzer. It is loaded in a separate class-loader to avoid ASM library version
 * clashes with the target code and so it is referred to by this interface.
 *
 * @author Roman Elizarov
 */
public interface TransformerAnalyzer extends ClassFileTransformer {
	/**
	 * Returns a list of immediate super-classes and super-interfaces of a given class or null if the class
	 * cannot be loaded in the specified class loader.
	 */
	public List<String> getImmediateClassParents(String className, ClassLoader loader);
}
