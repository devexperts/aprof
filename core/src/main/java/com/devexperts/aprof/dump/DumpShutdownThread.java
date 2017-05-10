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

import com.devexperts.aprof.AProfRegistry;
import com.devexperts.aprof.util.Log;

/**
 * @author Roman Elizarov
 */
public class DumpShutdownThread extends Thread {
	private final Dumper dumper;
	private final long finish;
	private final long trtime;
	private final DumpPeriodicThread dpt;

	public DumpShutdownThread(Dumper dumper, long finish, long trtime, DumpPeriodicThread dpt) {
		super("AprofDump-Shutdown");
		this.dumper = dumper;
		this.finish = finish;
		this.trtime = trtime;
		this.dpt = dpt;
	}

	@Override
	public void run() {
		Log.out.println("Shutting down...");
		if (dpt != null && !dpt.shutdown()) {
			return;
		}
		dumper.makeDump(true);
		long treal = System.currentTimeMillis() - finish;
		long ttrans = AProfRegistry.getTime() - trtime;
		Log.out.println("Stopped after " + treal + " ms with " + ttrans + " ms in transformer " +
			"(" + (treal - ttrans) + " ms other)");
	}
}
