package com.devexperts.aprof.hotspot;

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

import java.io.File;
import java.io.FilenameFilter;
import java.lang.management.ManagementFactory;
import java.util.HashSet;
import java.util.Set;

import com.devexperts.aprof.Configuration;
import com.devexperts.aprof.util.Log;

/**
 * Watches for "hs_cXXX_pidYYY.log" files in temp directory where HotSpot compile threads are writing their logs to.
 *
 * @author Roman Elizarov
 */
public class CompileLogWatcher extends Thread {
	private static final int INITIAL_SLEEP_TIME = 500;
	private static final int MAX_SLEEP_TIME = 10000;

	private final Configuration config;
	private final Set<String> seenNames = new HashSet<String>();

	public CompileLogWatcher(Configuration config) {
		super("CompileLogWatcher");
		setDaemon(true);
		this.config = config;
	}

	@Override
	public void run() {
		try {
			String name = ManagementFactory.getRuntimeMXBean().getName();
			int i = name.indexOf('@');
			if (i < 0)
				throw new IllegalArgumentException("Cannot extract pid from runtime name of '" + name + "'");
			String pid = name.substring(0, i);
			File tmpDir = new File(System.getProperty("java.io.tmpdir"));
			final String suffix = "_pid" + pid + ".log";
			FilenameFilter filter = new FilenameFilter() {
				public boolean accept(File dir, String name) {
					return name.startsWith("hs_c") && name.endsWith(suffix);
				}
			};
			long sleepTime = INITIAL_SLEEP_TIME;
			while (!Thread.currentThread().isInterrupted()) {
				scanFiles(tmpDir, filter);
				Thread.sleep(sleepTime);
				sleepTime = Math.min(MAX_SLEEP_TIME, sleepTime + sleepTime / 2);
			}
		} catch (Throwable t) {
			Log.out.println("Failed to watch for compile logs " + t);
			t.printStackTrace(Log.out);
		}
	}

	private void scanFiles(File tmpDir, FilenameFilter filter) {
		String[] names = tmpDir.list(filter);
		for (String name : names) {
			if (seenNames.add(name)) {
				CompileLogParser thread = new CompileLogParser(config, new File(tmpDir, name));
				thread.start();
			}
		}
	}

}
