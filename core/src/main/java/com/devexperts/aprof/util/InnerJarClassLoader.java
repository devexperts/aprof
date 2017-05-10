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
import java.net.URL;
import java.net.URLClassLoader;
import java.security.*;
import java.security.cert.Certificate;
import java.util.*;
import java.util.jar.*;

/**
 * This ClassLoader allows to load classes from URLs located inside JAR or ZIP files.
 *
 * @author Dmitry Paraschenko
 * @author Vitaly Trifanov
 * @author Devexperts LLC
 */
public class InnerJarClassLoader extends URLClassLoader {
	private static final String CLASS_FILE_SUFFIX = ".class";
	private static final int BUFFER_SIZE = 4096;

	/** The context to be used for loading classes and resources. */
	private final AccessControlContext acc;
	/** All cached classes. */
	private final Map<String, CachedClass> classes = new HashMap<String, CachedClass>();
	/** All cached classes and other resources. */
	private final Map<String, CachedResource> resources = new HashMap<String, CachedResource>();

	/** Creates class loader for specified JAR files. */
	public InnerJarClassLoader(URL... jars) throws IOException {
		this(Arrays.asList(jars));
	}

	/** Creates class loader for specified JAR files. */
	public InnerJarClassLoader(List<URL> jars) throws IOException {
		super(new URL[0]);
		acc = AccessController.getContext();
		for (URL jar : jars)
			cacheJar(jar);
	}

	@Override
	protected synchronized Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
		// First, check if the class has already been loaded
		Class clazz = findLoadedClass(name);
		if (clazz == null) {
			CachedClass cc = classes.get(name);
			if (cc != null)
				clazz = cc.define();
			if (clazz == null)
				clazz = getParent().loadClass(name);
		}
		if (resolve)
			resolveClass(clazz);
		return clazz;
	}

	@Override
	protected Class<?> findClass(String name) throws ClassNotFoundException {
		CachedClass cc = classes.get(name);
		if (cc != null)
			return cc.define();
		throw new ClassNotFoundException(name);
	}

	@Override
	public InputStream getResourceAsStream(String name) {
		CachedResource resource = resources.get(name);
		if (resource != null)
			return resource.openStream();
		return super.getResourceAsStream(name);
	}

	private void cacheJar(URL url) throws IOException {
		JarInputStream jarInputStream = new JarInputStream(url.openStream());
		try {
			Manifest manifest = jarInputStream.getManifest();
			while (true) {
				JarEntry entry = jarInputStream.getNextJarEntry();
				if (entry == null) {
					break;
				}
				if (entry.isDirectory()) {
					continue;
				}
				String name = entry.getName();
				int size = (int) entry.getSize();
				byte[] bytes;
				if (size > 0) {
					bytes = new byte[size];
					for (int read = 0; read < size; ) {
						int rd = jarInputStream.read(bytes, read, size - read);
						if (rd < 0) {
							throw new EOFException(url.getFile() + ", " + name + ": read " + read + " of " + size);
						}
						read += rd;
					}
				} else {
					// Size is unknown
					ByteArrayOutputStream baos = new ByteArrayOutputStream();
					byte[] buf = new byte[BUFFER_SIZE];
					int n;
					while ((n = jarInputStream.read(buf)) > 0) {
						baos.write(buf, 0, n);
					}
					bytes = baos.toByteArray();
				}
				if (name.endsWith(CLASS_FILE_SUFFIX)) {
					cacheClass(url, manifest, name, bytes, entry.getCertificates());
				} else {
					cacheResource(url, manifest, name, bytes, entry.getCertificates());
				}
			}
		} finally {
			jarInputStream.close();
		}
	}

	private void cacheClass(URL url, Manifest manifest, String name, byte[] bytes, Certificate[] certificates) {
		CachedClass cachedClass = new CachedClass(url, manifest, name, bytes, certificates);
		classes.put(cachedClass.className, cachedClass);
		resources.put(name, cachedClass);
	}

	private void cacheResource(URL url, Manifest manifest, String name, byte[] bytes, Certificate[] certificates) {
		resources.put(name, new CachedResource(url, manifest, name, bytes, certificates));
	}

	/** Cached resource. */
	private class CachedResource {
		protected final URL url;
		protected final Manifest manifest;
		protected final String resourceName;
		protected final byte[] bytes;
		protected final Certificate[] certificates;

		private CachedResource(URL url, Manifest manifest, String resourceName, byte[] bytes, Certificate[] certificates) {
			this.url = url;
			this.manifest = manifest;
			this.resourceName = resourceName;
			this.bytes = bytes;
			this.certificates = certificates;
		}

		/** Opens this resource as stream. */
		public InputStream openStream() {
			return new ByteArrayInputStream(bytes);
		}
	}

	/** Cached class. Should be defined with {@link #define()}. */
	private class CachedClass extends CachedResource {
		protected final String className;

		private CachedClass(URL url, Manifest manifest, String resourceName, byte[] bytes, Certificate[] certificates) {
			super(url, manifest, resourceName, bytes, certificates);
			assert resourceName.endsWith(CLASS_FILE_SUFFIX);
			className = resourceName.replace('/', '.').substring(0, resourceName.length() - CLASS_FILE_SUFFIX.length());
		}

		public Class<?> define() {
			try {
				return AccessController.doPrivileged(new PrivilegedExceptionAction<Class<?>>() {
					public Class<?> run() {
						int i = className.lastIndexOf('.');
						if (i != -1) {
							String packageName = className.substring(0, i);
							Package pkg = getPackage(packageName);
							if (pkg == null) {
								if (manifest != null) {
									definePackage(packageName, manifest, url);
								} else {
									definePackage(packageName, null, null, null, null, null, null, null);
								}
							}
						}
						CodeSource cs = new CodeSource(url, certificates);
						return defineClass(className, bytes, 0, bytes.length, cs);
					}
				}, acc);
			} catch (PrivilegedActionException pae) {
				pae.printStackTrace();
				throw new IllegalStateException(pae.getException());
			}
		}
	}
}
