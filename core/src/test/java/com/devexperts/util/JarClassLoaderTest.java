package com.devexperts.util;

import com.devexperts.util.JarClassLoader;

import java.io.File;
import java.io.IOException;

/**
 * @author Dmitry Paraschenko
 */
public class JarClassLoaderTest {
	public static void main(String[] args) throws IOException {
		JarClassLoader loader = new JarClassLoader(new File("aprof.jar").toURL());
		System.out.println(loader.getResourceAsStream("details.config"));
		System.out.println(loader.getResource("details.config"));
	}
}
