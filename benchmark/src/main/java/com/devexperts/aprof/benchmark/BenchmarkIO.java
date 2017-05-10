package com.devexperts.aprof.benchmark;

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
