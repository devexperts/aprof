/*
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 */

package com.devexperts.util;

import java.io.*;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.*;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.jar.Manifest;

/**
 * This ClassLoader allows to load classes from URLs located inside JAR or ZIP files.
 *
 * @author Dmitry Paraschenko
 * @author Vitaly Trifanov
 * @author Devexperts LLC
 */
public class JarClassLoader extends URLClassLoader {
	private static final String CLASS_FILE_SUFFIX = ".class";
	private static final int BUFFER_SIZE = 4096;

	/** The context to be used for loading classes and resources. */
	private final AccessControlContext acc;
	/** All cached classes. */
	private final List<CachedClass> classes = new ArrayList<CachedClass>();
	/** All cached classes and other resources. */
	private final List<CachedResource> resources = new ArrayList<CachedResource>();

	/** Creates class loader for specified JAR files. */
	public JarClassLoader(URL... jars) throws IOException {
		this(Arrays.asList(jars));
	}

	/** Creates class loader for specified JAR files. */
	public JarClassLoader(List<URL> jars) throws IOException {
		super(new URL[0]);
		acc = AccessController.getContext();
		for (URL jar : jars) {
			cacheJar(jar);
		}
	}

	/** Forces all classes with names starting with specified prefixed to be loaded. */
	public void forceLoad(String... prefixes) throws ClassNotFoundException {
		for (CachedClass clazz : classes) {
			for (String prefix : prefixes) {
				if (clazz.className.startsWith(prefix)) {
					loadClass(clazz.className);
				}
			}
		}
	}

	/** Forces all classes to be loaded. */
	public void forceLoadAllClasses() throws ClassNotFoundException {
		for (CachedClass clazz : classes) {
			loadClass(clazz.className);
		}
	}

	@Override
	protected synchronized Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
		// First, check if the class has already been loaded
		Class clazz = findLoadedClass(name);
		if (clazz == null) {
			try {
				clazz = findClass(name);
			} catch (ClassNotFoundException e) {
				// Do nothing
			}
			if (clazz == null) {
				clazz = getParent().loadClass(name);
			}
		}
		if (resolve) {
			resolveClass(clazz);
		}
		return clazz;
	}

	@Override
	protected Class<?> findClass(final String name) throws ClassNotFoundException {
		for (CachedClass clazz : classes) {
			if (name.equals(clazz.className)) {
				return clazz.define();
			}
		}
		throw new ClassNotFoundException(name);
	}

	@Override
	public InputStream getResourceAsStream(String name) {
		for (CachedResource resource : resources) {
			if (name.equals(resource.resourceName)) {
				return resource.openStream();
			}
		}
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
		classes.add(cachedClass);
		resources.add(cachedClass);
	}

	private void cacheResource(URL url, Manifest manifest, String name, byte[] bytes, Certificate[] certificates) {
		resources.add(new CachedResource(url, manifest, name, bytes, certificates));
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
