package com.devexperts.aprof;

/*-
 * #%L
 * Aprof Core
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

/**
 * @author Roman Elizarov
 */
public class Version {
	private static String compactVersion = createVersion(false);
	private static String fullVersion = createVersion(true);

	public static String compact() {
		return compactVersion;
	}

	public static String full() {
		return fullVersion;
	}

	private static String createVersion(boolean full) {
		Package p = Package.getPackage("com.devexperts.aprof");
		String version = p.getImplementationVersion();
		StringBuilder sb = new StringBuilder();
		sb.append("Aprof ");
		sb.append(version == null ? "version unknown" : version);
		if (full)
			sb.append(", Copyright (C) 2002-2017 Devexperts LLC");
		return sb.toString();
	}
}
