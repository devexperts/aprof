package com.devexperts.aprof.transformer;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.io.IOException;
import java.io.InputStream;

/**
 * @author Denis Davydov
 */
class ClassInfo {
	private final ClassLoader loader;
	private final Type type;
	private final int access;
	private final String superClass;
	private final String[] interfaces;

	ClassInfo(String type, ClassLoader loader) {
		this.loader = loader;
		this.type = Type.getObjectType(type);
		String s = type.replace('.', '/') + ".class";
		InputStream is = null;
		ClassReader cr;
		try {
			is = loader.getResourceAsStream(s);
			cr = new ClassReader(is);
		} catch (IOException e) {
			throw new RuntimeException(e);
		} finally {
			if (is != null) {
				try {
					is.close();
				} catch (Exception ignored) {
				}
			}
		}

		access = cr.getAccess();
		superClass = cr.getSuperName();
		interfaces = cr.getInterfaces();
	}

	Type getType() {
		return type;
	}

	int getModifiers() {
		return access;
	}

	ClassInfo getSuperclass() {
		if (superClass == null) {
			return null;
		}
		return new ClassInfo(superClass, loader);
	}

	ClassInfo[] getInterfaces() {
		if (interfaces == null) {
			return new ClassInfo[0];
		}
		ClassInfo[] result = new ClassInfo[interfaces.length];
		for (int i = 0; i < result.length; ++i) {
			result[i] = new ClassInfo(interfaces[i], loader);
		}
		return result;
	}

	boolean isInterface() {
		return (getModifiers() & Opcodes.ACC_INTERFACE) > 0;
	}

	private boolean implementsInterface(final ClassInfo that) {
		for (ClassInfo c = this; c != null; c = c.getSuperclass()) {
			ClassInfo[] tis = c.getInterfaces();
			for (ClassInfo ti : tis) {
				if (ti.type.equals(that.type) || ti.implementsInterface(that)) {
					return true;
				}
			}
		}
		return false;
	}

	private boolean isSubclassOf(final ClassInfo that) {
		for (ClassInfo c = this; c != null; c = c.getSuperclass()) {
			if (c.getSuperclass() != null
					&& c.getSuperclass().type.equals(that.type)) {
				return true;
			}
		}
		return false;
	}

	public boolean isAssignableFrom(final ClassInfo that) {
		if (this == that) {
			return true;
		}

		if (that.isSubclassOf(this)) {
			return true;
		}

		if (that.implementsInterface(this)) {
			return true;
		}

		return false;
	}
}
