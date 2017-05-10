package com.devexperts.aprof.benchmark;

/*-
 * #%L
 * JMH benchmarks
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

import java.io.*;

import org.openjdk.jmh.annotations.*;

@State(Scope.Thread)
public class BenchmarkIO {
	private final PrintWriter out = new PrintWriter(new LoopWriter());

	@GenerateMicroBenchmark
	public void testPrintChar() {
		out.print('.');
	}

	static class LoopWriter extends Writer {
		private static final int SIZE = 1 << 16; // must be power of 2

		char[] buf = new char[SIZE];
		int ptr;

		@Override
		public void write(char[] cbuf, int off, int len) throws IOException {
			while (len-- > 0)
				write(cbuf[off++]);
		}

		@Override
		public void write(int c) {
			buf[ptr++] = (char)c;
			ptr &= SIZE;
		}

		@Override
		public void flush() throws IOException {}

		@Override
		public void close() throws IOException {}
	}
}
