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

import java.io.IOException;
import java.io.InputStream;

/**
 * @author Roman Elizarov
 */
public class FastByteBuffer  {
	private byte[] buf = new byte[65536];
	private int size;

	public boolean isEmpty() {
		return size == 0;
	}

	public void clear() {
		size = 0;
	}


	private void ensureCapacity(int capacity) {
		if (buf.length >= capacity)
			return;
		byte[] bytes = new byte[Math.max(buf.length + buf.length / 2, capacity)];
		System.arraycopy(buf, 0, bytes, 0, size);
		buf = bytes;
	}

	public byte[] getBytes() {
		byte[] bytes = new byte[size];
		System.arraycopy(buf, 0, bytes, 0, size);
		return bytes;
	}

	public void readFrom(InputStream is) throws IOException {
		size = 0;
		int n;
		while ((n = is.available()) > 0) {
			ensureCapacity(size + n);
			n = is.read(buf, size, buf.length - size);
			if (n <= 0)
				return;
			size += n;
		}
	}

}
