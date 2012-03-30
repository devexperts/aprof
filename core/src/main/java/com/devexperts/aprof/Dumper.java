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

import com.devexperts.aprof.util.FastOutputStreamWriter;
import com.devexperts.aprof.util.Log;

import java.io.*;
import java.util.Date;

/**
 * @author Roman Elizarov
 */
class Dumper {
	private final Configuration config;
	private final String args_str;
	private final long start;
	private final Snapshot total = new Snapshot();
	private final Snapshot last = new Snapshot();
	private final Snapshot temp = new Snapshot();
	private long last_time = System.currentTimeMillis();

	private final DumpFormatter formatter;

	private int file_number = 0;

	private int snapshot_count = 0;
	private int overflow_count = 0;

	public Dumper(Configuration config, long start) {
		this.config = config;
		this.args_str = config.toString();
		this.start = start;
		this.formatter = new DumpFormatter(config);
	}

	public synchronized void makeOverflowSnapshot() {
		Log.out.println("Making snapshot to prevent overflow...");
		AProfRegistry.makeSnapshot(last);
		overflow_count++;
	}


	public synchronized void sendDumpTo(ObjectOutputStream oos, String address) throws IOException {
		Log.out.println("Sending dump over socket connection to " + address + " ...");
		AProfRegistry.makeSnapshot(last);
		temp.clearDeep();
		temp.addAll(total);
		temp.addAll(last);
		oos.writeObject(temp);
	}

	public synchronized void makeDump(boolean dump_all) {
		String file_name = config.getFile();
		if (file_name.length() == 0)
			return; // do not dump
		boolean file_append = snapshot_count > file_number && config.isFileAppend();

		int mask_end = file_name.lastIndexOf('#');
		if (mask_end >= 0) {
			file_number++;
			if (config.getFilecount() != 0)
				file_number %= config.getFilecount();
			int mask_start = mask_end;
			while (mask_start > 0 && file_name.charAt(mask_start - 1) == '#') {
				mask_start--;
			}
			StringBuilder sb = new StringBuilder();
			sb.append(file_name.substring(0, mask_start));
			String number = resize(String.valueOf(file_number), mask_end - mask_start + 1);
			sb.append(number);
			sb.append(file_name.substring(mask_end + 1));
			file_name = sb.toString();
		}

		Log.out.println("Writing dump to file " + file_name + "...");
		AProfRegistry.makeSnapshot(last);
		total.addAll(last);
		snapshot_count++;

		PrintWriter out = null;
		try {
			out = new PrintWriter(new FastOutputStreamWriter(new FileOutputStream(file_name, file_append)));
			dumpAll(out, dump_all);
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
		Log.out.println("Writing dump to file " + file_name + "... done");
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

	private void dumpAll(PrintWriter out, boolean dump_all) {
		long now = System.currentTimeMillis();
		long uptime = now - start;
		//------ Line #1
		out.println("===============================================================================");
		out.println(Version.get());
		//------ Line #2
		out.print("Allocation dump at ");
		out.print(new Date(now));
		out.print(". Uptime ");
		out.flush();
		DumpFormatter.printnum(out, uptime);
		out.print(" ms (");
		DumpFormatter.printtime(out, uptime);
		out.println(")");
		out.flush();
		//------ Line #3
		out.print("Arguments ");
		out.println(args_str);
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
		DumpFormatter.printnum(out, snapshot_count);
		out.print(" times to write file and ");
		DumpFormatter.printnum(out, overflow_count);
		out.println(" times to prevent overflow");
		//------ last line
		out.print("===============================================================================");
		out.flush();

		if (!dump_all) {
			out.println();
			out.println();
			out.print("LAST allocation dump for ");
			long delta = now - last_time;
			DumpFormatter.printnum(out, delta);
			last_time = now;
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
		formatter.dumpSection(out, total, dump_all ? 0 : config.getThreshold());
		out.println();
		out.println();
	}
}
