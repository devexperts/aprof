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
import java.net.Socket;
import java.util.Locale;

import com.devexperts.aprof.dump.DumpFormatter;
import com.devexperts.aprof.dump.SnapshotRoot;
import com.devexperts.aprof.util.FastOutputStreamWriter;
import com.devexperts.aproftest.TestCase;
import com.devexperts.aproftest.TestSuite;

/**
 * @author Denis Davydov
 */
public class AProfTools {

	private static final String STARTUP_NOTICE = Version.full() +
			"\nThis program comes with ABSOLUTELY NO WARRANTY." +
			"\nThis is free software, and you are welcome to redistribute it under certain conditions." +
			"\nSource code and documentation are available at <http://code.devexperts.com/>.";

	private static final String ENCODING = "UTF-8";

	public static void main(final String[] args) throws IOException, ClassNotFoundException {
		if (args.length > 0) {
			String command = args[0].trim().toLowerCase(Locale.US);
			if ("dump".equals(command)) {
				runDumpCommand(args);
				return;
			} else if ("export".equals(command)) {
				runExportCommand(args);
				return;
			} else if ("selftest".equals(command)) {
				runSelfTest(args);
				return;
			}
		}
		help();
	}

	private static void help() throws IOException {
		PrintWriter out = new PrintWriter(new FastOutputStreamWriter(System.out), true);
		out.println(STARTUP_NOTICE);
		out.println();
		out.println("Usage: java -javaagent:aprof.jar[=<args>] <other-JVM-options-and-main-class>");
		out.println("       Runs JVM with aprof agent to profile memory allocations.");
		out.println("Where <args> is <key>=<value>[:<key>=<value>[:...]]");
		out.println("Supported keys are:");
		Configuration def = new Configuration();
		for (Configuration.Prop p : Configuration.PROPS.values()) {
			out.println("  " + padr(p.getName(), 11) + " - " + p.getDescription());
			out.println(padr("", 16) + "Default value is \"" + def.getString(p) + "\".");
		}
		def.showNotes(out, true);
		out.println();
		out.println("Usage: java -jar aprof.jar dump [<host>:]<port>");
		out.println("       Dumps statistics from a running aprof agent that listen on a port.");
		out.println();
		out.println("Usage: java -jar aprof.jar export [<file>]");
		out.println("       Exports default tracked locations configuration to a file.");
		out.println();
		out.println("Usage: java -ea -javaagent:aprof.jar -jar aprof.jar selftest [<test> ...]");
		out.println("       Runs built-in self tests. Use 'all' for <test> to run all of them.");
	}

	private static String padr(String s, int len) {
		while (s.length() < len)
			s += " ";
		return s;
	}

	private static void helpSelfTest() throws IOException {
		PrintWriter out = new PrintWriter(new FastOutputStreamWriter(System.out), true);
		out.println(STARTUP_NOTICE);
		out.println();
		out.println("Usage: java -ea -javaagent:aprof.jar -jar aprof.jar selftest [<test> ...]");
		out.println("Where <test> is 'all' or one of tests:");
		for (TestCase test : TestSuite.getTestCases()) {
			out.println("\t" + test.name());
		}
	}

	private static void runDumpCommand(String[] args) throws IOException, ClassNotFoundException {
		if (args.length != 2) {
			help();
			return;
		}
		String address = args[1];
		int i = address.indexOf(':');
		String host = i < 0 ? "localhost" : address.substring(0, i);
		int port = Integer.parseInt(address.substring(i + 1));
		Socket socket = new Socket(host, port);
		OutputStream outputStream = socket.getOutputStream();
		outputStream.write("DUMP\r\n".getBytes(ENCODING));
		outputStream.flush();
		ObjectInputStream ois = new ObjectInputStream(socket.getInputStream());
		SnapshotRoot totalSnapshot = (SnapshotRoot)ois.readObject();
		ois.close();
		socket.close();
		DumpFormatter formatter = new DumpFormatter(new Configuration());
		PrintWriter out = new PrintWriter(System.out);
		formatter.dumpSnapshot(out, totalSnapshot, "DUMP");
		out.flush();
	}

	private static void runExportCommand(String[] args) throws IOException {
		if (args.length > 2) {
			help();
			return;
		}
		String fileName = args.length > 1 ? args[1].trim() : null;
		PrintWriter out = fileName == null ? new PrintWriter(System.out) : new PrintWriter(fileName);
		InputStream inputStream = ClassLoader.getSystemResourceAsStream(DetailsConfiguration.RESOURCE);
		BufferedReader in = new BufferedReader(new InputStreamReader(inputStream));
		while (true) {
			String line = in.readLine();
			if (line == null) {
				break;
			}
			out.println(line);
		}
		in.close();
		out.flush();
		if (fileName != null) {
			out.close();
		}
	}

	private static void runSelfTest(String[] args) throws IOException {
		if (args.length < 2) {
			helpSelfTest();
			return;
		}
		boolean ok = true;
		for (int i = 1; i < args.length; i++) {
			String testName = args[i].trim();
			if ("all".equalsIgnoreCase(testName)) {
				if (!TestSuite.testAllApplicableCases())
					ok = false;
				continue;
			}
			boolean done = false;
			for (TestCase test : TestSuite.getTestCases()) {
				if (test.name().equalsIgnoreCase(testName)) {
					if (!TestSuite.testSingleCase(test))
						ok = false;
					done = true;
				}
			}
			if (!done) {
				helpSelfTest();
				return;
			}
		}
		if (ok)
			System.out.println("==== TESTS HAVE PASSED");
		else
			System.out.println("==== SOME TESTS HAVE FAILED !!!");
	}
}
