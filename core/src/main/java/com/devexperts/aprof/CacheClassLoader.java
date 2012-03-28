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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.*;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.jar.Manifest;

/**
 * @author Dmitry Paraschenko
 */
class CacheClassLoader extends URLClassLoader {
    /* The context to be used when loading classes and resources */
    private final AccessControlContext acc;

	private boolean initialized;
	private final List<CachedClass> classes = Collections.synchronizedList(new ArrayList<CachedClass>());

	public CacheClassLoader() throws IOException {
		super(new URL[0]);
		acc = AccessController.getContext();
	}

	protected void initialized() {
		initialized = true;
	}

	protected List<String> getCachedClassNames() {
		List<String> names = new ArrayList<String>();
		for (CachedClass clazz : classes) {
			names.add(clazz.name);
		}
		return names;
	}

	@Override
	protected synchronized Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
		if (!initialized) {
			return super.loadClass(name, resolve);
		}
		// First, check if the class has already been loaded
		Class c = findLoadedClass(name);
		if (c == null) {
			try {
				c = findClass(name);
			} catch (ClassNotFoundException e) {
				// Do nothing
			}
			if (c == null) {
				c = getParent().loadClass(name);
			}
		}
		if (resolve) {
			resolveClass(c);
		}
		return c;
	}

	@Override
	protected Class<?> findClass(String name) throws ClassNotFoundException {
		for (CachedClass clazz : classes) {
			if (name.equals(clazz.name)) {
				return clazz.define();
			}
		}
		throw new ClassNotFoundException(name);
	}

	@Override
	public InputStream getResourceAsStream(String name) {
		for (CachedClass clazz : classes) {
			if (name.equals(clazz.getResourceName())) {
				return new ByteArrayInputStream(clazz.bytes);
			}
		}
		return super.getResourceAsStream(name);
	}

	protected void cacheClass(URL url, Manifest manifest, String name, byte[] bytes, Certificate[] certificates) {
		if (initialized) {
			throw new IllegalStateException("cannot cache class after initialization stage");
		}
		classes.add(new CachedClass(url, manifest, name, bytes, certificates));
	}

	private class CachedClass {
		private URL url;
		private Manifest manifest;
		private String name;
		private byte[] bytes;
		private Certificate[] certificates;

		private CachedClass(URL url, Manifest manifest, String name, byte[] bytes, Certificate[] certificates) {
			this.url = url;
			this.manifest = manifest;
			this.name = name;
			this.bytes = bytes;
			this.certificates = certificates;
		}

		private String getResourceName() {
			return name.replace('.', '/') + ".class";
		}

		private Class<?> define() {
			try {
				return AccessController.doPrivileged(new PrivilegedExceptionAction<Class<?>>() {
					public Class<?> run() {
						int i = name.lastIndexOf('.');
						if (i != -1) {
							String pkgname = name.substring(0, i);
							Package pkg = getPackage(pkgname);
							if (pkg == null) {
								if (manifest != null) {
									definePackage(pkgname, manifest, url);
								} else {
									definePackage(pkgname, null, null, null, null, null, null, null);
								}
							}
						}
						CodeSource cs = new CodeSource(url, certificates);
						return defineClass(name, bytes, 0, bytes.length, cs);
					}
				}, acc);
			} catch (PrivilegedActionException pae) {
				pae.printStackTrace();
				throw new IllegalStateException(pae.getException());
			}
		}
	}
}
