/*
 *  Aprof - Java Memory Allocation Profiler
 *  Copyright (C) 2002-2012  Devexperts LLC
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.devexperts.aprof.transformer;

import com.devexperts.aprof.AProfRegistry;
import com.devexperts.aprof.Configuration;
import com.devexperts.aprof.LocationStack;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;

/**
 * @author Dmitry Paraschenko
 */
class Context {
	private final Configuration config;
	private final String cname;
	private final String mname;
	private final String desc;
	private final int access;

	private final String location;
	private final boolean method_tracked;
	private final boolean object_init;
	private final String aprof_ops_impl;

	private int location_stack = -1;

	public Context(Configuration config, String cname, String mname, String desc, int access) {
		this.config = config;
		this.cname = cname;
		this.mname = mname;
		this.desc = desc;
		this.access = access;

		this.location = getLocationString(cname, mname, desc);
		this.method_tracked = !mname.startsWith(AProfTransformer.ACCESS_METHOD) && !AProfRegistry.isInternalClass(cname) && AProfRegistry.isLocationTracked(location);
		this.object_init = this.cname.equals(AProfTransformer.OBJECT_CLASS_NAME) && this.mname.equals(AProfTransformer.INIT);
		this.aprof_ops_impl = AProfRegistry.isInternalClass(this.cname) ? AProfTransformer.APROF_OPS_INTERNAL : AProfTransformer.APROF_OPS;
	}

	public Configuration getConfig() {
		return config;
	}

	public String getClassName() {
		return cname;
	}

	public String getMethodName() {
		return mname;
	}

	public String getDescription() {
		return desc;
	}

	public int getAccess() {
		return access;
	}

	public String getLocation() {
		return location;
	}

	public boolean isMethodTracked() {
		return method_tracked;
	}

	public boolean isObjectInit() {
		return object_init;
	}

	public String getAprofOpsImplementation() {
		return aprof_ops_impl;
	}

	public void declareLocationStack(GeneratorAdapter mv) {
		int location_stack = mv.newLocal(Type.getType(LocationStack.class));
		mv.visitInsn(Opcodes.ACONST_NULL);
		mv.storeLocal(location_stack);
		this.location_stack = location_stack;
	}

	public void pushLocationStack(GeneratorAdapter mv) {
		if (location_stack < 0) {
			mv.visitMethodInsn(Opcodes.INVOKESTATIC, AProfTransformer.LOCATION_STACK, "get", AProfTransformer.NOARG_RETURNS_STACK);
			return;
		}

		Label done = new Label();
		mv.loadLocal(location_stack);
		mv.dup();
		mv.ifNonNull(done);
		mv.pop();
		mv.visitMethodInsn(Opcodes.INVOKESTATIC, AProfTransformer.LOCATION_STACK, "get", AProfTransformer.NOARG_RETURNS_STACK);
		mv.dup();
		mv.storeLocal(location_stack);
		mv.visitLabel(done);
	}

	public String getLocationString(String cname, String mname, String desc) {
		cname = cname.replace('/', '.'); // to make sure it can be called both on cname and owner descs.
		StringBuilder sb = new StringBuilder(cname.length() + 1 + mname.length());
		sb.append(cname).append(".").append(mname);
		String location = sb.toString();
		for (String s : config.getSignatureLocations())
			if (location.equals(s)) {
				convertDesc(sb, desc);
				location = sb.toString();
				break;
			}
		return location;
	}

	private void convertDesc(StringBuilder sb, String desc) {
		sb.append('(');
		Type[] types = Type.getArgumentTypes(desc);
		for (int i = 0; i < types.length; i++) {
			if (i > 0)
				sb.append(',');
			appendShortType(sb, types[i]);
		}
		sb.append(')');
		appendShortType(sb, Type.getReturnType(desc));
	}

	private void appendShortType(StringBuilder sb, Type type) {
		if (type == Type.VOID_TYPE)
			return;
		String s = type.getClassName();
		sb.append(s, s.lastIndexOf('.') + 1, s.length());
	}
}
