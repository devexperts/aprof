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

import java.io.IOException;
import java.io.InputStream;

import org.objectweb.asm.*;

/**
 * Reads and keeps very limited information about a class in a specific class loader,
 * so that {@link FrameClassWriter} can be implemented without having to actually load
 * any classes through class loader.
 *
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
			throw new RuntimeException("Cannot load type '" + type + "'", e);
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
