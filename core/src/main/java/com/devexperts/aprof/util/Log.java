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

/**
 * @author Roman Elizarov
 */
public class Log {
	private static final String HEADER = ": aprof: ";

	public static PrintWriter out = new LogPrintWriter(System.out);

	public static void initFile(String logFile) {
		if (logFile.length() == 0)
			return;
		try {
			out = new LogPrintWriter(new FileOutputStream(logFile));
		} catch (IOException e) {
			out.println("Failed to log to file: " + e);
			e.printStackTrace(out);
		}
	}

	private static class LogPrintWriter extends PrintWriter {
		public LogPrintWriter(OutputStream out) {
			super(new FastOutputStreamWriter(out), true);
		}

		public void println(Object x) {
			if (x instanceof CharSequence)
				println((CharSequence)x);
			else
				println(String.valueOf(x));
		}

		public void println(String x) {
			synchronized (System.out) {
				FastFmtUtil.printTimeAndDate(this, System.currentTimeMillis());
				print(HEADER);
				super.println(x);
			}
		}

		private void println(CharSequence cs) {
			synchronized (System.out) {
				FastFmtUtil.printTimeAndDate(this, System.currentTimeMillis());
				print(HEADER);
				for (int i = 0; i < cs.length(); i++)
					super.print(cs.charAt(i));
				super.println();
			}
		}
	}
}
