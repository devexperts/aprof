package com.devexperts.aprof.hotspot;

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
