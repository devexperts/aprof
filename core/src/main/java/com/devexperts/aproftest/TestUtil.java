package com.devexperts.aproftest;

import java.text.NumberFormat;
import java.util.*;

/**
 * @author Roman Elizarov
 */
public class TestUtil {
	private TestUtil() {} // do not create

	private static NumberFormat FORMAT = NumberFormat.getNumberInstance(Locale.US);

	static {
		FORMAT.setGroupingUsed(true);
	}

	public static String fmt(String format, String... keyValues) {
		Map<String,String> kv = new HashMap<String, String>();
		for (String keyValue : keyValues) {
			int i = keyValue.indexOf('=');
			if (i < 0)
				throw new IllegalArgumentException("'=' is not found");
			kv.put(keyValue.substring(0, i), keyValue.substring(i + 1));
		}
		StringBuilder sb = new StringBuilder();
		int i = 0;
		while (true) {
			int j = format.indexOf('{', i);
			if (j < 0)
				j = format.length();
			sb.append(format.substring(i, j));
			if (j >= format.length())
				break;
			int k = format.indexOf('}', j + 1);
			if (k < 0)
				throw new IllegalArgumentException("'}' is not found");
			String key = format.substring(j + 1, k);
			if (!kv.containsKey(key))
				throw new IllegalArgumentException("Unknown key '" + key + "'");
			sb.append(kv.get(key));
			i = k + 1;
		}
		return sb.toString();
	}

	public static String fmt(long count) {
		return FORMAT.format(count);
	}
}
