package com.devexperts.util;

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

import java.io.File;
import java.io.IOException;

import com.devexperts.aprof.util.InnerJarClassLoader;

/**
 * @author Dmitry Paraschenko
 */
public class InnerJarClassLoaderTest {
	public static void main(String[] args) throws IOException {
		InnerJarClassLoader loader = new InnerJarClassLoader(new File("aprof.jar").toURL());
		System.out.println(loader.getResourceAsStream("details.config"));
		System.out.println(loader.getResource("details.config"));
	}
}
