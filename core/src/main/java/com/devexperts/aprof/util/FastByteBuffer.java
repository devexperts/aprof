package com.devexperts.aprof.util;

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
