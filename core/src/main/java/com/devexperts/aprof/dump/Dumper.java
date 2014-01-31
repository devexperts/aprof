/*
 * Aprof - Java Memory Allocation Profiler
 * Copyright (C) 2002-2013  Devexperts LLC
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

package com.devexperts.aprof.dump;

import java.io.*;
import java.util.Date;

import com.devexperts.aprof.*;
import com.devexperts.aprof.util.FastOutputStreamWriter;
import com.devexperts.aprof.util.Log;

/**
 * @author Roman Elizarov
 */
public class Dumper {
	private final Configuration config;
	private final String argsStr;
	private final long start;
	private final SnapshotDeep total = new SnapshotDeep();
	private final SnapshotDeep last = new SnapshotDeep();
	private final SnapshotDeep temp = new SnapshotDeep();
	private long lastTime = System.currentTimeMillis();

	private final DumpFormatter formatter;

	private int fileNumber = 0;

	private int snapshotCount = 0;
	private int overflowCount = 0;

	public Dumper(Configuration config, long start) {
		this.config = config;
		this.argsStr = config.toString();
		this.start = start;
		this.formatter = new DumpFormatter(config);
	}

	public synchronized void makeOverflowSnapshot() {
		Log.out.println("Making snapshot to prevent overflow...");
		AProfRegistry.takeSnapshot(last);
		overflowCount++;
	}


	public synchronized void sendDumpTo(ObjectOutputStream oos, String address) throws IOException {
		Log.out.println("Sending dump over socket connection to " + address + " ...");
		AProfRegistry.takeSnapshot(last);
		temp.clearDeep();
		temp.addDeep(total);
		temp.addDeep(last);
		oos.writeObject(temp);
	}

	public synchronized void makeDump(boolean dumpAll) {
		String fileName = config.getFile();
		if (fileName.length() == 0)
			return; // do not dump
		boolean fileAppend = snapshotCount > fileNumber && config.isFileAppend();

		int maskEnd = fileName.lastIndexOf('#');
		if (maskEnd >= 0) {
			fileNumber++;
			if (config.getFilecount() != 0)
				fileNumber %= config.getFilecount();
			int maskStart = maskEnd;
			while (maskStart > 0 && fileName.charAt(maskStart - 1) == '#') {
				maskStart--;
			}
			StringBuilder sb = new StringBuilder();
			sb.append(fileName.substring(0, maskStart));
			String number = resize(String.valueOf(fileNumber), maskEnd - maskStart + 1);
			sb.append(number);
			sb.append(fileName.substring(maskEnd + 1));
			fileName = sb.toString();
		}

		Log.out.println("Writing dump to file " + fileName + "...");
		AProfRegistry.takeSnapshot(last);
		total.addDeep(last);
		snapshotCount++;

		PrintWriter out = null;
		try {
			out = new PrintWriter(new FastOutputStreamWriter(new FileOutputStream(fileName, fileAppend)));
			dumpAll(out, dumpAll);
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (out != null)
				try {
					out.close();
				} catch (Exception e) {
					e.printStackTrace();
				}
		}
		last.clearDeep();
		Log.out.println("Writing dump to file " + fileName + "... done");
	}

	private String resize(String str, int length) {
		if (str.length() >= length) {
			return str.substring(str.length() - length);
		}
		StringBuilder sb = new StringBuilder();
		while (str.length() < length) {
			sb.append('0');
			length--;
		}
		sb.append(str);
		return sb.toString();
	}

	private void dumpAll(PrintWriter out, boolean dumpAll) {
		long now = System.currentTimeMillis();
		long uptime = now - start;
		//------ Line #1
		out.println("===============================================================================");
		out.println(Version.full());
		//------ Line #2
		out.print("Allocation dump at ");
		out.print(new Date(now));
		out.print(". Uptime ");
		DumpFormatter.printnum(out, uptime);
		out.print(" ms (");
		DumpFormatter.printtime(out, uptime);
		out.println(")");
		//------ Line #3
		out.print("Arguments ");
		out.println(argsStr);
		//------ Line #4
		out.print("Transformed ");
		DumpFormatter.printnum(out, AProfRegistry.getCount());
		out.print(" classes and registered ");
		DumpFormatter.printnum(out, AProfRegistry.getLocationCount());
		out.print(" locations in ");
		DumpFormatter.printp(out, AProfRegistry.getTime(), uptime);
		out.print(" ms");
		out.println();
		//------ Line #4
		out.print("Snapshot of counters was made ");
		DumpFormatter.printnum(out, snapshotCount);
		out.print(" times to write file and ");
		DumpFormatter.printnum(out, overflowCount);
		out.println(" times to prevent overflow");
		//------ last line
		out.print("===============================================================================");

		if (!dumpAll) {
			out.println();
			out.println();
			out.print("LAST allocation dump for ");
			long delta = now - lastTime;
			DumpFormatter.printnum(out, delta);
			lastTime = now;
			out.print(" ms (");
			DumpFormatter.printtime(out, delta);
			out.println(")");
			formatter.dumpSection(out, last, config.getThreshold());
		}

		out.println();
		out.println();
		out.print("TOTAL allocation dump for ");
		DumpFormatter.printnum(out, uptime);
		out.print(" ms (");
		DumpFormatter.printtime(out, uptime);
		out.println(")");
		formatter.dumpSection(out, total, dumpAll ? 0 : config.getThreshold());
		out.println();
		out.println();
	}
}
