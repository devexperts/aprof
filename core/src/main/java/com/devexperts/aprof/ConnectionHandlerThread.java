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

import java.net.Socket;
import java.io.*;
import java.util.Locale;

/**
 * @author Denis Davydov
 */
class ConnectionHandlerThread extends Thread {
	private static final String ENCODING = "UTF-8";

	private final Socket s;
	private final Dumper dumper;
	private final String address;

	public ConnectionHandlerThread(Socket s, Dumper dumper) {
		this(s, dumper, s.getInetAddress().getHostAddress() + ":" + s.getPort());
	}

	private ConnectionHandlerThread(Socket s, Dumper dumper, String address) {
		super("AProf-Connection-" + address);
		setDaemon(true);
		this.s = s;
		this.dumper = dumper;
		this.address = address;
	}

	public void run() {
		try {
			BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), ENCODING));
			OutputStream out = s.getOutputStream();
			String line;
			while ((line = in.readLine()) != null) {
				line = line.trim().toUpperCase(Locale.US);
				if (line.equals("DUMP")) {
					sendDump(out);
					s.close();
					return;
				} else if (line.equals("BYE")) {
					s.close();
					return;
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void sendDump(OutputStream out) {
		try {
			ByteArrayOutputStream buf = new ByteArrayOutputStream();
			ObjectOutputStream oos = new ObjectOutputStream(buf);
			dumper.sendDumpTo(oos, address);
			oos.close();
			byte[] bytes = buf.toByteArray();
			out.write(bytes);
			out.flush();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
