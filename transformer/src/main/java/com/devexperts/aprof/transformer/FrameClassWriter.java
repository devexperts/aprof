package com.devexperts.aprof.transformer;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;

class FrameClassWriter extends ClassWriter {
	FrameClassWriter(ClassReader classReader) {
		super(classReader, ClassWriter.COMPUTE_MAXS + ClassWriter.COMPUTE_FRAMES);
	}

	/**
	 * The reason of overriding is to avoid ClassCircularityError which occurs during processing of classes related
	 * to java.util.TimeZone
	 */
	@Override
	protected String getCommonSuperClass(String type1, String type2) {
		ClassLoader classLoader = getClass().getClassLoader();
		ClassInfo c, d;
		try {
			c = new ClassInfo(type1, classLoader);
			d = new ClassInfo(type2, classLoader);
		} catch (Throwable e) {
			throw new RuntimeException(e);
		}

		if (c.isAssignableFrom(d)) {
			return type1;
		}

		if (d.isAssignableFrom(c)) {
			return type2;
		}

		if (c.isInterface() || d.isInterface()) {
			return "java/lang/Object";
		} else {
			do {
				c = c.getSuperclass();
			} while (c != null && !c.isAssignableFrom(d));

			return c == null ? "java/lang/Object" : c.getType().getInternalName();
		}
	}
}
