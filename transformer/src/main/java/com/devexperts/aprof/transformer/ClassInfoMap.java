package com.devexperts.aprof.transformer;

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

	public ClassInfo get(String internalClassName) {
		return map.get(internalClassName);
	}

	public void put(String internalClassName, ClassInfo classInfo) {
		map.put(internalClassName, classInfo);
	}

}

