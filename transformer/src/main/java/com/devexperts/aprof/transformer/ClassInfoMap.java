package com.devexperts.aprof.transformer;

/*-
 * #%L
 * Aprof Transformer
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

import java.util.HashMap;
import java.util.Map;

public class ClassInfoMap {
	private final Map<String, ClassInfo> map = new HashMap<String, ClassInfo>();

	// make sure at most one thread does initialization
	private Thread initTrackedClassesThread = Thread.currentThread();

	public synchronized boolean isInitTrackedClasses() {
		return initTrackedClassesThread != null;
	}

	public synchronized void doneInit() {
		initTrackedClassesThread = null;
		notifyAll();
	}

	public synchronized void waitInit() throws InterruptedException {
		while (initTrackedClassesThread != null && initTrackedClassesThread != Thread.currentThread())
			wait();
	}

	public synchronized ClassInfo get(String internalClassName) {
		return map.get(internalClassName);
	}

	public synchronized void put(String internalClassName, ClassInfo classInfo) {
		if (!map.containsKey(internalClassName))
			map.put(internalClassName, classInfo);
	}
}

