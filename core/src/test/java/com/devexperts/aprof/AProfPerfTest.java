/*
 * Aprof - Java Memory Allocation Profiler
 * Copyright (C) 2002-2014  Devexperts LLC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.devexperts.aprof;

import java.io.*;

/**
 * @author Roman Elizarov
 */
public class AProfPerfTest {
	private String jdkDir = "C:\\Java\\jdk1.5.0_11\\";
	private String javacPath = jdkDir + "bin\\javac";
	private String sourceDir = jdkDir + "demo\\jfc\\SwingSet2\\src\\";
	private String sources = sourceDir + "*.java";
	private String aprofJar = "deploy\\aprof.jar";
	private String outDir = "perf.out\\";
	private String globalArgs = "quiet=true:file=:time=0";

	public static void main(String[] args) throws IOException, InterruptedException {
		new AProfPerfTest().go();
	}

	private void go() throws IOException, InterruptedException {
		new File(outDir).mkdirs();
		for (int pass = 1; pass <= 3; pass++) {
			System.out.println("====== PASS # " + pass + " ======");
			makeRun("arrays-:reflect-:location-:unknown-:detailed-:size-:frames-");
			makeRun("arrays+:reflect-:location-:unknown-:detailed-:size-:frames-");
			makeRun("arrays-:reflect+:location-:unknown-:detailed-:size-:frames-");
			makeRun("arrays-:reflect-:location+:unknown-:detailed-:size-:frames-");
			makeRun("arrays-:reflect-:location-:unknown+:detailed-:size-:frames-");
			makeRun("arrays+:reflect+:location+:unknown+:detailed-:size-:frames-");
			makeRun("arrays+:reflect-:location-:unknown-:detailed-:size+:frames-");
			makeRun("arrays-:reflect+:location-:unknown-:detailed-:size+:frames-");
			makeRun("arrays-:reflect-:location-:unknown+:detailed-:size+:frames-");
			makeRun("arrays+:reflect+:location+:unknown+:detailed-:size+:frames-");
			makeRun("arrays+:reflect+:location+:unknown+:detailed+:size+:frames-");
			makeRun("arrays-:reflect-:location-:unknown-:detailed-:size-:frames+");
			makeRun("arrays+:reflect-:location-:unknown-:detailed-:size-:frames+");
			makeRun("arrays-:reflect+:location-:unknown-:detailed-:size-:frames+");
			makeRun("arrays-:reflect-:location+:unknown-:detailed-:size-:frames+");
			makeRun("arrays-:reflect-:location-:unknown+:detailed-:size-:frames+");
			makeRun("arrays+:reflect+:location+:unknown+:detailed+:size+:frames+");
		}
	}

	private void makeRun(String args) throws IOException, InterruptedException {
		String sa = globalArgs + (args.length() > 0 ? ":" + args : "");
		System.out.println("*** Running with " + sa);
		ProcessBuilder pb = new ProcessBuilder(javacPath,
			"-J-javaagent:" + aprofJar + "=" + sa,
			"-sourcepath", sourceDir,
			"-d", outDir,
			"-nowarn",
			sources);
		pb.redirectErrorStream(true);
		long time = System.currentTimeMillis();
		Process p = pb.start();
		Thread t = new EchoThread(p.getInputStream());
		t.start();
		p.waitFor();
		System.out.println("Completed in " + (System.currentTimeMillis() - time) + " ms");
	}

	private static class EchoThread extends Thread {
		private final InputStream is;

		public EchoThread(InputStream is) {
			super("EchoThread");
			this.is = is;
		}

		public void run() {
			byte[] buf = new byte[4096];
			int n;
			try {
				while ((n = is.read(buf)) >= 0)
					System.out.write(buf, 0, n);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

	}
}
