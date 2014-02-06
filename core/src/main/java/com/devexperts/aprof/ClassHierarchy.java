package com.devexperts.aprof;

import java.util.*;

/**
 * This class keeps information about class parents and its virtual methods, so that
 * tracking method lists can be expanded to track their invocations via inherited virtual or interface invoke.
 * Instances of this classes are cached forever by transformer and differences between different versions
 * of the same class in different class loaders are ignored. Caching is performed by name only.
 *
 * @author Roman Elizarov
 */
public class ClassHierarchy {
	private String superClass;
	private final List<String> declaredInterfaces = new ArrayList<String>();
	private final Set<String> declaredVirtualMethods = new HashSet<String>();
	private final Set<String> allVirtualMethods = new HashSet<String>();

	public String getSuperClass() {
		return superClass;
	}

	public void setSuperClass(String superClass) {
		this.superClass = superClass;
	}

	public List<String> getDeclaredInterfaces() {
		return declaredInterfaces;
	}

	public Set<String> getDeclaredVirtualMethods() {
		return declaredVirtualMethods;
	}

	/**
	 * Returns a list of declared and inherited virtual methods.
	 */
	public Set<String> getAllVirtualMethods() {
		return allVirtualMethods;
	}
}
