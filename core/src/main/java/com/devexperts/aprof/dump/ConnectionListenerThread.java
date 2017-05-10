package com.devexperts.aprof.dump;

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

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * @author Denis Davydov
 */
public class ConnectionListenerThread extends Thread {
	private final int port;
	private final Dumper dumper;

	public ConnectionListenerThread(int port, Dumper dumper) {
		super("Aprof-ConnectionListener");
		setDaemon(true);
		this.port = port;
		this.dumper = dumper;
	}

	@Override
	public void run() {
		try {
			ServerSocket ss = new ServerSocket(port);
			while (!Thread.interrupted()) {
				Socket s = ss.accept();
				s.setSoTimeout(60000);
				Thread t = new ConnectionHandlerThread(s, dumper);
				t.start();
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
