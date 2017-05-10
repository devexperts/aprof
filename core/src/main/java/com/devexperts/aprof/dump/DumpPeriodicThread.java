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
public class DumpPeriodicThread extends Thread {
	private static final long SLEEP_TIME = 1000;

	private final Dumper dumper;
	private final long time;

	private volatile boolean running = true;

	public DumpPeriodicThread(Dumper dumper, long time) {
		super("AprofDump-Periodic");
		setDaemon(true);
		setPriority(Thread.MAX_PRIORITY);
		this.dumper = dumper;
		this.time = time;
	}

	public boolean shutdown() {
		running = false;
		this.interrupt();
		try {
			this.join();
			return true;
		} catch (InterruptedException e) {
			e.printStackTrace();
			return false;
		}
	}

	@Override
	public void run() {
		while (running) {
			try {
				long waitDump = time;
				//noinspection InfiniteLoopStatement
				while (running) {
					Thread.sleep(SLEEP_TIME);
					if (time > 0 && (waitDump -= SLEEP_TIME) <= 0) {
						dumper.makeDump(false);
						waitDump = time;
					} else if (AProfRegistry.isOverflowThreshold()) {
						dumper.makeOverflowSnapshot();
					}
				}
			} catch (InterruptedException e) {
				// thread dies
				if (running) {
					Log.out.println(getName() + " was interrupted");
				}
				return;
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
}
