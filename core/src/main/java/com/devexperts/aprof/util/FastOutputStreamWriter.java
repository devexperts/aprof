package com.devexperts.aprof.util;

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

import java.io.*;

/**
 * @author Roman Elizarov
 */
public class FastOutputStreamWriter extends Writer {
	private static final char[] HEX = "0123456789abcdef".toCharArray();

	private final BufferedOutputStream out;

	public FastOutputStreamWriter(OutputStream out) {
		this.out = new BufferedOutputStream(out);
	}

	public void flush() throws IOException {
		out.flush();
	}

	public void close() throws IOException {
		out.close();
	}

	private void write(char c) throws IOException {
		if (c <= 0x7f)
			out.write(c);
		else {
			out.write('\\');
			out.write('u');
			out.write(HEX[(c >> 12) & 0xf]);
			out.write(HEX[(c >> 8) & 0xf]);
			out.write(HEX[(c >> 4) & 0xf]);
			out.write(HEX[c & 0xf]);
		}
	}

	public void write(int c) throws IOException {
		write((char)c);
	}

	public void write(char cbuf[], int off, int len) throws IOException {
		for (int i = off; i < off + len; i++)
			write(cbuf[i]);
	}

	public void write(String str, int off, int len) throws IOException {
		for (int i = off; i < off + len; i++)
			write(str.charAt(i));
	}

	public Writer append(CharSequence csq) throws IOException {
		if (csq == null)
			write("null");
		else
			append(csq, 0, csq.length());
		return this;
	}

	public Writer append(CharSequence csq, int start, int end) throws IOException {
		if (csq == null)
			write("null");
		else
			for (int i = start; i < end; i++)
				write(csq.charAt(i));
		return this;
	}
}
