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

import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * @author Roman Elizarov
 */
public class FastFmtUtil {
	private FastFmtUtil() {} // do not create

	private static final int TEAR_LINE_LENGTH = 120;

	private static final ThreadLocal<TimeAndDateFormatter> TD_FMT = new ThreadLocal<TimeAndDateFormatter>();

	public static void printlnTearLine(PrintWriter out, char c) {
		for (int i = 0; i < TEAR_LINE_LENGTH; i++)
			out.print(c);
		out.println();
	}

	public static void printNum(PrintWriter out, long value) {
		if (value < 0) {
			out.print('-');
			value = -value;
		}
		boolean fill = false;
		for (long x = 1000000000000000000L; x >= 1; x /= 1000) {
			if (value >= x || fill) {
				if (fill)
					out.print(",");
				print3(out, (int)(value / x), fill);
				value = value % x;
				fill = true;
			}
		}
		if (!fill)
			out.print("0");
	}

	public static void print3(PrintWriter out, int value, boolean fill) {
		if (fill || value >= 100)
			out.print((char)(value / 100 + '0'));
		print2(out, value, fill);
	}

	public static void print2(PrintWriter out, int value, boolean fill) {
		if (fill || value >= 10)
			out.print((char)(value / 10 % 10 + '0'));
		out.print((char)(value % 10 + '0'));
	}

	public static void printAvg(PrintWriter out, long size, long count) {
		out.print("(avg size ");
		printNum(out, Math.round((double)size / count));
		out.print(" bytes)");
	}

	public static void printNumPercent(PrintWriter out, long count, long total) {
		printNum(out, count);
		if (count > 0 && total > 0) {
			out.print(" (");
			long pp = count * 10000 / total;
			printNum(out, pp / 100);
			out.print(".");
			print2(out, (int)(pp % 100), true);
			out.print("%)");
		}
	}

	public static void printTimePeriod(PrintWriter out, long millis) {
		long hour = millis / (60 * 60000);
		int min = (int)(millis / 60000 % 60);
		int sec = (int)(millis / 1000 % 60);
		printNum(out, hour);
		out.print("h");
		print2(out, min, true);
		out.print("m");
		print2(out, sec, true);
		out.print("s");
	}

	public static void printTimeAndDate(PrintWriter out, long millis) {
		TimeAndDateFormatter fmt = TD_FMT.get();
		if (fmt == null)
			TD_FMT.set(fmt = new TimeAndDateFormatter());
		fmt.print(out, millis);
	}

	private static class TimeAndDateFormatter {
		private static final long SECOND = 1000;
		private static final long MINUTE = 60 * SECOND;
		private static final long HOUR = 60 * MINUTE;

		SimpleDateFormat dayFmt = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
		SimpleDateFormat hourFmt = new SimpleDateFormat("HH", Locale.US);
		SimpleDateFormat zoneFmt = new SimpleDateFormat("Z", Locale.US);
		long lastHourStart;
		String lastDay;
		String lastHour;
		String lastZone;

		public void print(PrintWriter out, long millis) {
			if (lastHourStart == 0 || millis < lastHourStart || millis >= lastHourStart + HOUR) {
				lastHourStart = millis / HOUR * HOUR;
				Date date = new Date(lastHourStart);
				lastDay = dayFmt.format(date);
				lastHour = hourFmt.format(date);
				lastZone = zoneFmt.format(date);
			}
			out.print(lastDay);
			out.print('T');
			out.print(lastHour);
			out.print(':');
			print2(out, (int)((millis / MINUTE) % 60), true);
			out.print(':');
			print2(out, (int)((millis / SECOND) % 60), true);
			out.print('.');
			print3(out, (int)(millis % SECOND), true);
			out.print(lastZone);
		}
	}
}
