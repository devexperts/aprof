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

package com.devexperts.aprof;

/**
 * @author Roman Elizarov
 */
public class Version {
	private static String compact_version = createVersion(false);
	private static String full_version = createVersion(true);

	public static String compact() {
		return compact_version;
	}

	public static String full() {
		return full_version;
	}

	private static String createVersion(boolean with_vendor) {
		Package p = Package.getPackage("com.devexperts.aprof");
		String title = p.getImplementationTitle();
		String version = p.getImplementationVersion();
		String vendor = p.getImplementationVendor();
		StringBuilder sb = new StringBuilder();
		sb.append(title == null ? "Aprof" : title).append(" ");
		sb.append(version == null ? "version unknown" : version);
		if (with_vendor) {
			sb.append(", Copyright (C) ").append(vendor == null ? "unknown" : vendor);
		}
		return sb.toString();
	}
}
