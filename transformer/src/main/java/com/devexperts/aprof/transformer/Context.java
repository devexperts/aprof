/*
 * Aprof - Java Memory Allocation Profiler
 * Copyright (C) 2002-2013  Devexperts LLC
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

import com.devexperts.aprof.AProfRegistry;
import com.devexperts.aprof.Configuration;
import org.objectweb.asm.Type;

/**
 * @author Dmitry Paraschenko
 */
class Context {
	private final Configuration config;
	private final String locationClass;
	private final String location;
    private final boolean accessMethod;
	private final boolean methodTracked;
	private final boolean objectInit;
	private final String aprofOpsImpl;

	private boolean locationStackNeeded = false;

	private int locationStack = -1;

	public Context(Configuration config, String cname, String mname, String desc, int access) {
		this.config = config;
		this.locationClass = AProfRegistry.normalize(cname);
        String locationMethod = getLocationMethod(locationClass, mname, desc);
        this.location = locationClass + "." + locationMethod;
        this.accessMethod = mname.startsWith(AProfTransformer.ACCESS_METHOD);
		this.methodTracked = !isInternalLocation() && isLocationTracked(locationClass, locationMethod);
		this.objectInit = this.locationClass.equals(AProfTransformer.OBJECT_CLASS_NAME) && mname.equals(AProfTransformer.OBJECT_INIT);
		this.aprofOpsImpl = isInternalLocation() ? AProfTransformer.APROF_OPS_INTERNAL : AProfTransformer.APROF_OPS;
	}

	public boolean isInternalLocation() {
		return AProfRegistry.isInternalLocationClass(locationClass);
	}

	public Configuration getConfig() {
		return config;
	}

	public String getLocationClass() {
		return locationClass;
	}

    public String getLocation() {
		return location;
	}

	public boolean isMethodTracked() {
		return methodTracked;
	}

	public boolean isObjectInit() {
		return objectInit;
	}

	public String getAprofOpsImplementation() {
		return aprofOpsImpl;
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

    public String getLocationMethod(String locationClass, String mname, String desc) {
        for (String s : config.getSignatureLocations())
            if (locationClass.equals(s)) {
                StringBuilder sb = new StringBuilder(mname);
                convertDesc(sb, desc);
                return sb.toString();
            }
        return mname;
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

	public boolean isLocationTracked(String locationClass, String locationMethod) {
        return config.isLocationTracked(locationClass, locationMethod) && !accessMethod;
    }
}
