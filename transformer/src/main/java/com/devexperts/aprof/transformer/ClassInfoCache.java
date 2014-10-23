package com.devexperts.aprof.transformer;

import com.devexperts.aprof.Configuration;
import com.devexperts.aprof.util.Log;
import org.objectweb.asm.ClassReader;

import java.io.InputStream;
import java.util.*;

/**
 * Caches class info for each class loader. Reference to class loader is never explicitly stored and is
 * always passed in arguments, so that there is not strong references to it. They are cached using
 * weak references to make class loaders eligible for garbage collection despite this cache.
 */
class ClassInfoCache {
	private final Configuration config;

	// ClassLoader -> internalClassName -> ClassInfo
	private WeakHashMap<ClassLoader, ClassInfoMap> classInfoCache =
		new WeakHashMap<ClassLoader, ClassInfoMap>();

	ClassInfoCache(Configuration config) {
		this.config = config;
	}

	synchronized ClassInfo getClassInfo(String internalClassName, ClassLoader loader) {
		while (true) {
			ClassInfo classInfo = getOrInitClassInfoMap(loader).get(internalClassName);
			if (classInfo != null)
				return classInfo;
			if (loader == null)
				return null;
			loader = loader.getParent();
		}
	}

	// Returns null if not found or failed to load
	synchronized ClassInfo getOrBuildClassInfo(String internalClassName, ClassLoader loader) {
		ClassInfo classInfo = getOrInitClassInfoMap(loader).get(internalClassName);
		if (classInfo != null)
			return classInfo;
		ClassInfoMap classInfoMap = classInfoCache.get(loader);
		classInfo = buildClassInfo(internalClassName, loader, classInfoMap.isInitTrackedClasses());
		if (classInfo != null)
			classInfoMap.put(internalClassName, classInfo);
		return classInfo;
	}

	// throws RuntimeException if not found or failed to load
	synchronized ClassInfo getOrBuildRequiredClassInfo(String internalClassName, ClassLoader loader) {
		ClassInfo classInfo = getOrBuildClassInfo(internalClassName, loader);
		if (classInfo == null)
			throw new RuntimeException("Cannot load class information for " + internalClassName.replace('/', '.'));
		return classInfo;
	}

	synchronized ClassInfoMap getOrInitClassInfoMap(ClassLoader loader) {
		ClassInfoMap classInfoMap = classInfoCache.get(loader);
		if (classInfoMap == null) {
			// make sure we have parent loader's map first
			if (loader != null)
				getOrInitClassInfoMap(loader.getParent());
			// at first time when class loader is discovered, tracked classes in this class loader are cached
			classInfoCache.put(loader, classInfoMap = new ClassInfoMap());
			initTrackedClasses(loader);
			classInfoMap.doneInit();
		} else
			try {
				classInfoMap.waitInit();
			} catch (InterruptedException e) {
				StringBuilder sb = new StringBuilder("Interrupted while waiting to initialize tracking classes");
				TransformerUtil.describeClassLoaderForLog(sb, loader);
				Log.out.println(sb);
				Thread.currentThread().interrupt();
			}
		return classInfoMap;
	}

	// classInfoMap: internalClassName -> ClassInfo
	private void initTrackedClasses(ClassLoader loader) {
		if (config.isVerboseTracked()) {
			StringBuilder sb = new StringBuilder("Initializing tracked classes info");
			TransformerUtil.describeClassLoaderForLog(sb, loader);
			Log.out.println(sb);
		}
		// using a queue of classes to analyze, load all tracked classes and their parents in hierarchy
		List<ClassInfo> queue = new ArrayList<ClassInfo>();
		Set<ClassInfo> inQueue = new HashSet<ClassInfo>();
		for (String trackedClassName : config.getTrackedClasses()) {
			String internalClassName = trackedClassName.replace('.', '/');
			ClassInfo classInfo = getOrBuildClassInfo(internalClassName, loader);
			if (classInfo == null || classInfo.isSealed())
				continue; // not found or sealed class info from parent class loader -- skip (don't add to analyze queue)
			queue.add(classInfo);
			inQueue.add(classInfo);
		}
		for (int i = 0; i < queue.size(); i++) {
			ClassInfo classInfo = queue.get(i);
			enqueueClass(classInfo.getSuperclassInfo(this, loader), queue, inQueue);
			for (ClassInfo intf : classInfo.getInterfaceInfos(this, loader))
				enqueueClass(intf, queue, inQueue);
		}
		// push virtual methods down hierarchy, e.g. virtual methods = virtual methods + inherited
		for (ClassInfo classInfo : queue)
			pushDownVirtualMethods(classInfo, loader);
		// Compute tracked method invocations for each class we've analyzed
		for (ClassInfo classInfo : queue) {
			// figure out which tracked methods this class actually has as virtual methods
			Map<String, Set<String>> virtualMethods = classInfo.getVirtualMethods();
			for (String virtualMethod : virtualMethods.keySet()) {
				if (config.isMethodTracked(classInfo.getClassName(), virtualMethod)) {
					// copy all declared signatures into a tracking list
					Map<String, Set<String>> methodNameMap = classInfo.getTrackedMethodInvocations();
					Set<String> descSet = methodNameMap.get(virtualMethod);
					if (descSet == null)
						methodNameMap.put(virtualMethod, descSet = new HashSet<String>());
					descSet.addAll(virtualMethods.get(virtualMethod));
				}
			}
			// pull tracked method invocations up the class hierarchy
			pullUpTrackedMethodInvocations(classInfo, classInfo.getTrackedMethodInvocations(), loader);
		}
		// seal class infos that we've loaded -- no changes into them from now on
		for (ClassInfo classInfo : queue) {
			classInfo.markSealed();
			if (config.isVerboseTracked() && !classInfo.getTrackedMethodInvocations().isEmpty()) {
				StringBuilder sb = new StringBuilder();
				sb.append("Tracking invocations on class ");
				sb.append(classInfo.getClassName());
				sb.append(" methods ");
				sb.append(classInfo.getTrackedMethodInvocations().keySet());
				TransformerUtil.describeClassLoaderForLog(sb, loader);
				Log.out.println(sb);
			}
		}
	}

	private void pushDownVirtualMethods(ClassInfo classInfo, ClassLoader loader) {
		if (classInfo.isSealed() || classInfo.hasInheritedMethods())
			return;
		processInheritedVirtualMethods(classInfo.getSuperclassInfo(this, loader), classInfo, loader);
		for (ClassInfo intf : classInfo.getInterfaceInfos(this, loader))
			processInheritedVirtualMethods(intf, classInfo, loader);
		classInfo.markInheritedMethods();
	}

	private void processInheritedVirtualMethods(ClassInfo superClassInfo, ClassInfo classInfo, ClassLoader loader) {
		if (superClassInfo == null)
			return;
		pushDownVirtualMethods(superClassInfo, loader);
		Map<String, Set<String>> superVirtualMethods = superClassInfo.getVirtualMethods();
		if (superVirtualMethods == null)
			return; // out of luck -- we don't know parent's inherited methods, because it was already loaded in parent
		Map<String, Set<String>> virtualMethods = classInfo.getVirtualMethods();
		for (Map.Entry<String, Set<String>> entry : superVirtualMethods.entrySet()) {
			String name = entry.getKey();
			Set<String> descSet = virtualMethods.get(name);
			if (descSet == null)
				virtualMethods.put(name, descSet = new HashSet<String>());
			descSet.addAll(entry.getValue());
		}
	}

	// classInfoMap: binaryClassName -> ClassInfo
	private void pullUpTrackedMethodInvocations(ClassInfo classInfo, Map<String, Set<String>> methods,
		ClassLoader loader)
	{
		if (methods.isEmpty())
			return;
		processOverridenTrackedMethodInvocation(classInfo.getSuperclassInfo(this, loader), methods, loader);
		for (ClassInfo intf : classInfo.getInterfaceInfos(this, loader))
			processOverridenTrackedMethodInvocation(intf, methods, loader);
	}

	// classInfoMap: binaryClassName -> ClassInfo
	private void processOverridenTrackedMethodInvocation(ClassInfo classInfo, Map<String, Set<String>> methods,
		ClassLoader loader)
	{
		if (classInfo == null || classInfo.isSealed())
			return; // not found or already sealed -- bad luck
		// intersect with this class's list of virtual methods
		Map<String, Set<String>> remainingMethods = new HashMap<String, Set<String>>();
		for (Map.Entry<String, Set<String>> entry : methods.entrySet()) {
			String name = entry.getKey();
			Set<String> descSet = classInfo.getVirtualMethods().get(name);
			if (descSet != null) {
				Set<String> inheritedDescSet = new HashSet<String>(descSet);
				inheritedDescSet.retainAll(entry.getValue());
				if (inheritedDescSet.isEmpty())
					continue;
				Map<String, Set<String>> trackedMethodInvocations = classInfo.getTrackedMethodInvocations();
				Set<String> trackedDescSet = trackedMethodInvocations.get(name);
				if (trackedDescSet == null)
					trackedMethodInvocations.put(name, trackedDescSet = new HashSet<String>());
				trackedDescSet.addAll(inheritedDescSet);
				remainingMethods.put(name, inheritedDescSet);
			}
		}
		// go further up hierarchy
		pullUpTrackedMethodInvocations(classInfo, remainingMethods, loader);

	}

	private void enqueueClass(ClassInfo classInfo, List<ClassInfo> queue, Set<ClassInfo> inQueue) {
		if (classInfo == null || classInfo.isSealed())
			return; // not found or already sealed -- do not analyze
		if (inQueue.add(classInfo))
			queue.add(classInfo);
	}

	private ClassInfo buildClassInfo(String internalClassName, ClassLoader loader, boolean loadVirtualMethods) {
		// check if parent class loader has this class info
		if (loader != null)  {
			ClassInfo classInfo = getOrBuildClassInfo(internalClassName, loader.getParent());
			if (classInfo != null)
				return classInfo;
		}
		// actually build it
		try {
			String classFileName = internalClassName + ".class";
			InputStream in;
			if (loader == null)
				in = getClass().getResourceAsStream("/" + classFileName);
			else
				in = loader.getResourceAsStream(classFileName);
			if (in == null)
				return null;
			ClassInfoVisitor visitor = new ClassInfoVisitor(loadVirtualMethods);
			try {
				ClassReader cr = new ClassReader(in);
				cr.accept(visitor, ClassReader.SKIP_DEBUG + ClassReader.SKIP_FRAMES + ClassReader.SKIP_CODE);
			} finally {
                in.close();
			}
			return visitor.result;
		} catch (Throwable t) {
			StringBuilder sb = new StringBuilder("Failed to load class ");
			sb.append(internalClassName);
			TransformerUtil.describeClassLoaderForLog(sb, loader);
			sb.append(" because of exception ");
			sb.append(t);
			Log.out.println(sb);
			return null;
		}
	}
}
