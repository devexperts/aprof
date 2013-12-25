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

package com.devexperts.aprof.util;

import java.io.PrintWriter;

/**
 * @author Roman Elizarov
 */
public class Log {
	private static final String HEADER = "aprof: ";

	public static PrintWriter out = new PrintWriter(new FastOutputStreamWriter(System.out), true) {
		public void println(Object x) {
			if (x instanceof CharSequence)
				println((CharSequence)x);
			else
				println(String.valueOf(x));
		}

		public void println(String x) {
			synchronized (System.out) {
				super.print(HEADER);
				super.println(x);
			}
		}

		private void println(CharSequence cs) {
			synchronized (System.out) {
				super.print(HEADER);
				for (int i = 0; i < cs.length(); i++)
					super.print(cs.charAt(i));
				super.println();
			}
		}
	};
}
