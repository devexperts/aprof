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

import java.io.EOFException;
import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.jar.Manifest;

/**
 * @author Dmitry Paraschenko
 */
class BulkClassLoader extends CacheClassLoader {
	private boolean loaded;

	public BulkClassLoader(List<URL> urls) throws IOException {
		for (URL url : urls) {
			cacheJar(url);
		}
		initialized();
	}

	public synchronized void loadAllClasses() {
		if (loaded)
			return;
		for (String name : getCachedClassNames()) {
			try {
				loadClass(name);
			} catch (ClassNotFoundException e) {
				e.printStackTrace();
			}
		}
		loaded = true;
	}

	private void cacheJar(URL url) throws IOException {
		JarInputStream jar_input_stream = new JarInputStream(url.openStream());
		Manifest manifest = jar_input_stream.getManifest();
		while (true) {
			JarEntry entry = jar_input_stream.getNextJarEntry();
			if (entry == null) {
				break;
			}
			String name = entry.getName();
			if (name.endsWith("/")) {
				continue;
			}
			if (!name.endsWith(".class")) {
				continue;
			}
			name = name.replace('/', '.').substring(0, name.length() - 6);
			int size = (int) entry.getSize();
			byte[] bytes = new byte[size];
			for (int read = 0; read < size;) {
				int rd = jar_input_stream.read(bytes, read, size - read);
				if (rd < 0) {
					throw new EOFException(url.getFile() + ", " + name + ": read " + read + " of " + size);
				}
				read += rd;
			}
			cacheClass(url, manifest, name, bytes, entry.getCertificates());
		}
		jar_input_stream.close();
	}
}
