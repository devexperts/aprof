package com.devexperts.aprof.hotspot;

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
 * Method tag from hotspot compilation log. For example:
 * "&ltmethod id='729' holder='646' name='indexOf' return='634' arguments='634 634' flags='1' bytes='70' iicount='3836'/&gt;"
 *
 * @author Roman Elizarov
 */
class Method extends IdNamedObject {
	int holder;
	int returns;
	int nArguments;
	int[] arguments = new int[4];

	public void addArgument(int arg) {
		if (nArguments >= arguments.length) {
			int[] newArguments = new int[nArguments * 2];
			System.arraycopy(arguments, 0, newArguments, 0, nArguments);
			arguments = newArguments;
		}
		arguments[nArguments++] = arg;
	}
}
