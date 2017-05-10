package com.devexperts.aprof.hotspot;

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

import java.io.*;

import com.devexperts.aprof.*;
import com.devexperts.aprof.util.Log;

/**
 * Parser for hotspot compile log files.
 *
 * @author Roman Elizarov
 */
class CompileLogParser extends Thread {
	private static final int SLEEP_TIME = 100;

	private static final String[] TAGS = {
		"task",
		"type",
		"klass",
		"method",
		"eliminate_allocation",
		"jvms"
	};

	private static final String[][] TAG_ATTRS = {
		{"compile_id"},
		{"id", "name"},
		{"id", "name"},
		{"id", "name", "holder", "return", "arguments"},
		{"type"},
		{"bci", "method"}
	};

	private static final int TAG_TASK = 0;
	private static final int TAG_TYPE = 1;
	private static final int TAG_KLASS = 2;
	private static final int TAG_METHOD = 3;
	private static final int TAG_ELIMINATE_ALLOCATION = 4;
	private static final int TAG_JVMS = 5;

	private static final int STATE_START = 0;
	private static final int STATE_TAG_PICK = 1;
	private static final int STATE_TAG = 2;
	private static final int STATE_TAG_ATTRS = 3;
	private static final int STATE_TAG_ATTR_PICK = 4;
	private static final int STATE_TAG_ATTR = 5;
	private static final int STATE_TAG_ATTR_VALUE_BEGIN = 6;
	private static final int STATE_TAG_ATTR_VALUE = 7;

	private static final int STATE_OTHER = 8;

	private final Configuration config;
	private final File compileLogFile;

	private int state = STATE_START;
	private int strIndex;
	private int strMask;
	private boolean tagClosed;
	private int tag;
	private int tagAttr;
	private StringBuilder[] tagAttrs = new StringBuilder[5];

	private int compileTaskId;
	private int eliminateAllocationType;
	private boolean eliminateAllocationFirstLoc;
	private IdNamedIndex<Type> types = new IdNamedIndex<Type>();
	private IdNamedIndex<Method> methods = new IdNamedIndex<Method>();
	private StringBuilder message = new StringBuilder();
	private StringBuilder temp = new StringBuilder();

	public CompileLogParser(Configuration config, File compileLogFile) {
		super("CompileLogParser-" + compileLogFile);
		setDaemon(true);
		this.compileLogFile = compileLogFile;
		this.config = config;
		for (int i = 0; i < tagAttrs.length; i++)
			tagAttrs[i] = new StringBuilder();
	}

	@Override
	public void run() {
		try {
			Log.out.println("Parsing HotSpot compile log file " + compileLogFile);
			InputStream in = new FileInputStream(compileLogFile);
			try {
				parse(in);
			} finally {
				in.close();
			}
		} catch (Throwable t) {
			Log.out.println("Failed to parse HotSpot compile log '" + compileLogFile + "' " + t);
			t.printStackTrace(Log.out);
		}
	}

	private void parse(InputStream in) throws IOException, InterruptedException {
		byte[] buf = new byte[8192];
		while (true) {
			int read = in.read(buf);
			if (read < 0) {
				// sleep & retry
				Thread.sleep(SLEEP_TIME);
				continue;
			}
			for (int i = 0; i < read; i++)
				parseChar((char)buf[i]);
		}
	}

	private void parseChar(char c) {
		if (c == '\r' || c == '\n') {
			state = STATE_START;
			return;
		}
		switch (state) {
		case STATE_START:
			if (c == '<') {
				state = STATE_TAG_PICK;
				strIndex = 0;
				strMask = (1 << TAGS.length) - 1;
				tagClosed = false;
			} else
				state = STATE_OTHER;
			break;
		case STATE_TAG_PICK:
			if (strIndex == 0 && c == '/' && !tagClosed) {
				tagClosed = true;
			} else {
				tag = updateMask(c, TAGS, STATE_TAG, STATE_OTHER);
				if (tag >= 0)
					for (StringBuilder tagValue : tagAttrs)
						tagValue.setLength(0); // clear values
			}
			break;
		case STATE_TAG:
			String tagString = TAGS[tag];
			if (strIndex == tagString.length() && (c == ' ' || c == '>' || c == '/')) {
				if (c == ' ') {
					state = STATE_TAG_ATTRS;
				} else {
					if (c == '/')
						tagClosed = true;
					processParsedTag();
					state = STATE_OTHER;
				}
			} else if (strIndex < tagString.length() && c == tagString.charAt(strIndex)) {
				strIndex++;
			} else {
				state = STATE_OTHER;
			}
			break;
		case STATE_TAG_ATTRS:
			if (c == '>') {
				processParsedTag();
				state = STATE_OTHER;
			} else if (c == '/') {
			  	tagClosed = true;
				processParsedTag();
				state = STATE_OTHER;
			} else if (c != ' ') {
				state = STATE_TAG_ATTR_PICK;
				strIndex = 0;
				String[] tagAttrs = TAG_ATTRS[tag];
				strMask = (1 << tagAttrs.length) - 1;
				tagAttr = updateMask(c, tagAttrs, STATE_TAG_ATTR, STATE_TAG_ATTR);
			}
			break;
		case STATE_TAG_ATTR_PICK:
			tagAttr = updateMask(c, TAG_ATTRS[tag], STATE_TAG_ATTR, STATE_TAG_ATTR);
			break;
		case STATE_TAG_ATTR:
			String tagAttrString = tagAttr >= 0 ? TAG_ATTRS[tag][tagAttr] : "";
			if (c == '=') {
				state = STATE_TAG_ATTR_VALUE_BEGIN;
			} else if (strIndex < tagAttrString.length() && c == tagAttrString.charAt(strIndex)) {
				strIndex++;
			} else {
				tagAttr = -1;
			}
			break;
		case STATE_TAG_ATTR_VALUE_BEGIN:
			if (c == '\'') {
				state = STATE_TAG_ATTR_VALUE;
				if (tagAttr >= 0)
					tagAttrs[tagAttr].setLength(0);
			} else
				state = STATE_OTHER;
			break;
		case STATE_TAG_ATTR_VALUE:
			if (c == '\'') {
				state = STATE_TAG_ATTRS;
			} else {
				if (tagAttr >= 0)
					tagAttrs[tagAttr].append(c);
			}
			break;
		case STATE_OTHER:
			// nothing special to do here -- wait till end of line.
			break;
		default:
			throw new AssertionError("cannot happen");
		}
	}

	private int updateMask(char c, String[] strings, int stateNext, int stateEmpty) {
		if (strMask == 0)
			return -1;
		for (int i = 0; i < strings.length; i++)
			if (((1 << i) & strMask) != 0 && (strIndex >= strings[i].length() || strings[i].charAt(strIndex) != c))
				strMask &= ~(1 << i);
		if (strMask == 0) {
			state = stateEmpty;
			return -1;
		}
		strIndex++;
		if (Integer.bitCount(strMask) == 1) {
			state = stateNext;
			return Integer.numberOfTrailingZeros(strMask);
		}
		return -1;
	}

	private int getIntAttr(int attr) {
		StringBuilder s = tagAttrs[attr];
		int result = 0;
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if (c < '0' || c > '9')
				return -1;
			result = result * 10 + (c - '0');
		}
		return result;
	}

	private void takeArgumentsAttr(Method method, int attr) {
		method.nArguments = 0;
		StringBuilder s = tagAttrs[attr];
		int result = 0;
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if (c == ' ') {
				if (result > 0)
					method.addArgument(result);
				result = 0;
			} else if (c < '0' || c > '9')
				return;
			result = result * 10 + (c - '0');
		}
		if (result > 0)
			method.addArgument(result);
	}

	private void takeNameAttr(IdNamedObject namedObject, int attr) {
		StringBuilder temp = namedObject.name;
		namedObject.name = tagAttrs[attr];
		tagAttrs[attr] = temp;
	}

	private void processParsedTag() {
		switch (tag) {
		case TAG_TASK:
			if (tagClosed) {
				compileTaskId = 0;
				eliminateAllocationType = 0;
				types.releaseAll();
				methods.releaseAll();
			} else
				compileTaskId = getIntAttr(0);
			break;
		case TAG_TYPE:
		case TAG_KLASS:
			if (compileTaskId > 0) {
				Type type = types.acquire();
				if (type == null)
					type = new Type();
				type.id = getIntAttr(0);
				type.klass = tag == TAG_KLASS;
				takeNameAttr(type, 1);
				types.add(type);
			}
			break;
		case TAG_METHOD:
			if (compileTaskId > 0) {
				Method method = methods.acquire();
				if (method == null)
					method = new Method();
				method.id = getIntAttr(0);
				takeNameAttr(method, 1);
				method.holder = getIntAttr(2);
				method.returns = getIntAttr(3);
				takeArgumentsAttr(method, 4);
				methods.add(method);
			}
			break;
		case TAG_ELIMINATE_ALLOCATION:
			if (compileTaskId > 0) {
				if (tagClosed) {
					doneEliminateAllocation(message);
					eliminateAllocationType = 0;
				} else {
					eliminateAllocationType = getIntAttr(0);
					eliminateAllocationFirstLoc = true;
					beginEliminateAllocation(message);
				}
			}
			break;
		case TAG_JVMS:
			if (eliminateAllocationType > 0)
				addEliminateAllocationAt(message, getIntAttr(0), getIntAttr(1));
			break;
		}
	}

	private void beginEliminateAllocation(StringBuilder sb) {
		if (config.isVerboseEliminateAllocation()) {
			sb.setLength(0);
			sb.append("HotSpot compile task ").append(compileTaskId);
			sb.append(" had eliminated allocation of ");
			appendClassName(sb, eliminateAllocationType);
		}
	}

	private void addEliminateAllocationAt(StringBuilder sb, int bci, int methodId) {
		if (config.isVerboseEliminateAllocation()) {
			sb.append("\n\tat ");
			appendFullMethodDescription(sb, methodId);
			sb.append(" bci=");
			sb.append(bci);
		}
		if (config.isCheckEliminateAllocation() && eliminateAllocationFirstLoc) {
			eliminateAllocationFirstLoc = false;
			Type type = types.get(eliminateAllocationType);
			Method method = methods.get(methodId);
			if (type != null && method != null) {
				temp.setLength(0);
				appendClassName(temp, eliminateAllocationType);
				DatatypeInfo datatypeInfo = AProfRegistry.registerDatatypeInfo(AProfRegistry.normalize(temp.toString()));
				temp.setLength(0);
				appendFQMethodName(temp, method);
				if (config.isSignatureLocation(temp))
					appendMethodSignature(temp, method);
				int loc = AProfRegistry.registerLocation(temp.toString());
				RootIndexMap rootIndexMap = AProfRegistry.registerRootIndex(datatypeInfo, loc);
				rootIndexMap.setPossiblyEliminatedAllocation();
			}
		}
	}

	private void doneEliminateAllocation(StringBuilder sb) {
		if (config.isVerboseEliminateAllocation()) {
			Log.out.println(message);
		}
	}

	private void appendClassName(StringBuilder sb, int id) {
		Type type = types.get(id);
		if (type == null) {
			sb.append("<unknown>");
			return;
		}
		AProfRegistry.resolveClassName(sb, type.name);
	}

	private void appendFullMethodDescription(StringBuilder sb, int id) {
		Method method = methods.get(id);
		if (method == null) {
			sb.append("<unknown>");
			return;
		}
		appendFQMethodName(sb, method);
		appendMethodSignature(sb, method);
		sb.append(' ');
		appendClassName(sb, method.returns);
	}

	private void appendFQMethodName(StringBuilder sb, Method method) {
		appendClassName(sb, method.holder);
		sb.append('.');
		sb.append(method.name);
	}

	private void appendMethodSignature(StringBuilder sb, Method method) {
		sb.append('(');
		for (int i = 0; i < method.nArguments; i++) {
			if (i > 0)
				sb.append(',');
			appendClassName(sb, method.arguments[i]);
		}
		sb.append(')');
	}
}
