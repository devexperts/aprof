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

import java.util.Set;

import com.devexperts.aprof.*;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

/**
 * @author Dmitry Paraschenko
 */
class Context {
	private final Configuration config;
	private final ClassInfoCache ciCache;
	private final ClassLoader loader;
	private final String locationClass;
	private final String locationMethod;
	private final String locationDesc;
	private final boolean accessMethod;
	private final boolean methodBodyTracked;
	private final boolean objectInit;
	private final boolean intrinsicArraysCopyOf;
	private final String aprofOpsImpl;

	private String location; // lazily computed on first get

	private boolean transformationNeeded;
	private boolean locationStackNeeded;

	private int locationStack = -1;

	public Context(Configuration config, ClassInfoCache ciCache, ClassLoader loader,
		String binaryClassName, String cname, String mname, String desc)
	{
		this.config = config;
		this.ciCache = ciCache;
		this.loader = loader;
		this.locationClass = AProfRegistry.normalize(cname);
		this.locationMethod = mname;
		this.accessMethod = mname.startsWith(TransformerUtil.ACCESS_METHOD);
		this.locationDesc = desc;
		this.methodBodyTracked = !isInternalLocation() && config.isMethodTracked(cname, mname) && !accessMethod;
		this.objectInit = locationClass.equals(TransformerUtil.OBJECT_CLASS_NAME) && mname.equals(TransformerUtil.INIT);
		this.intrinsicArraysCopyOf = TransformerUtil.isIntrinsicArraysCopyOf(binaryClassName, mname, desc);
		this.aprofOpsImpl = isInternalLocation() ? TransformerUtil.APROF_OPS_INTERNAL : TransformerUtil.APROF_OPS;
	}

	/**
	 * Returns true for context of {@link Internal} location.
	 */
	public boolean isInternalLocation() {
		return AProfRegistry.isInternalLocation(locationClass);
	}

	public Configuration getConfig() {
		return config;
	}

	public String getLocation() {
		if (location == null)
			location = buildLocationString();
		return location;
	}

	private String buildLocationString() {
		StringBuilder sb = new StringBuilder(locationClass.length() + locationMethod.length() + 1);
		sb.append(locationClass);
		sb.append('.');
		sb.append(locationMethod);
		if (config.isSignatureLocation(sb))
			buildSignatureString(sb);
		return sb.toString();
	}

	private void buildSignatureString(StringBuilder sb) {
		sb.append('(');
		Type[] types = Type.getArgumentTypes(locationDesc);
		for (int i = 0; i < types.length; i++) {
			if (i > 0)
				sb.append(',');
			appendShortType(sb, types[i]);
		}
		sb.append(')');
	}

	public boolean isMethodInvocationTracked(String cname, int opcode, String owner, String name, String desc,
		int transformedClassVersion)
	{
		if (owner.startsWith("["))
			return false; // do not track array method invocations
		if (transformedClassVersion >= Opcodes.V1_7 && TransformerUtil.INIT.equals(name))
			return false; // <init> invocations don't pass verifier in modern JVMs
		if (config.isMethodTracked(cname, name))
			return true; // direct invocation of tracked method through its actual class
		if (opcode != Opcodes.INVOKEVIRTUAL && opcode != Opcodes.INVOKEINTERFACE)
			return false; // special or static invocation cannot got though superclass/super interface
		// Check for invocation via super class or interface
		// Load target class information
		ClassInfo classInfo = ciCache.getClassInfo(owner, loader);
		if (classInfo == null)
			return false; // we don't have class info, which means we don't track it, then
		// Check if tracked method
		Set<String> descSet = classInfo.getTrackedMethodInvocations().get(name);
		return descSet != null && descSet.contains(desc);
	}

	public boolean isMethodBodyTracked() {
		return methodBodyTracked;
	}

	public boolean isObjectInit() {
		return objectInit;
	}

	public boolean isIntrinsicArraysCopyOf() {
		return intrinsicArraysCopyOf;
	}

	public String getAprofOpsImplementation() {
		return aprofOpsImpl;
	}

	public boolean isTransformationNeeded() {
		return transformationNeeded;
	}

	public void setTransformationNeeded(boolean transformationNeeded) {
		this.transformationNeeded = transformationNeeded;
	}

	public boolean isLocationStackNeeded() {
		return locationStackNeeded;
	}

	public void setLocationStackNeeded(boolean locationStackNeeded) {
		this.locationStackNeeded = locationStackNeeded;
	}

	public int getLocationStack() {
		return locationStack;
	}

	public void setLocationStack(int locationStack) {
		this.locationStack = locationStack;
	}

	private void appendShortType(StringBuilder sb, Type type) {
		if (type == Type.VOID_TYPE)
			return;
		String s = type.getClassName();
		sb.append(s, s.lastIndexOf('.') + 1, s.length());
	}

	@Override
	public String toString() {
		return "Context{" +
			"locationClass='" + locationClass + '\'' +
			", locationMethod='" + locationMethod + '\'' +
			", locationDesc='" + locationDesc + '\'' +
			", accessMethod=" + accessMethod +
			", methodTracked=" + methodBodyTracked +
			", objectInit=" + objectInit +
			", intrinsicArraysCopyOf=" + intrinsicArraysCopyOf +
			", aprofOpsImpl='" + aprofOpsImpl + '\'' +
			", location='" + location + '\'' +
			", transformationNeeded=" + transformationNeeded +
			", locationStackNeeded=" + locationStackNeeded +
			", locationStack=" + locationStack +
			'}';
	}

}
