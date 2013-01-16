/*
 * Aprof - Java Memory Allocation Profiler
 * Copyright (C) 2002-2013  Devexperts LLC
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

package com.devexperts.aproftest;

import com.devexperts.aprof.Configuration;

import java.io.*;
import java.util.ArrayList;

/**
 * @author Dmitry Paraschenko
 */
class DeserializationTest implements TestCase {
	private final ArrayList<Entity> serializedObject;
	private final byte[] serializedData;

	public DeserializationTest() {
		serializedObject = new ArrayList<Entity>();
		for (int i = 0; i < 100000; i++) {
			serializedObject.add(new Entity(i));
		}
		try {
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			ObjectOutputStream oos = new ObjectOutputStream(baos);
			oos.writeObject(serializedObject);
			serializedData = baos.toByteArray();
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}


	public String name() {
		return "deserialization";
	}

	public String verifyConfiguration(Configuration configuration) {
		return null;
	}

	public String[] getCheckedClasses() {
		return new String[] {getClass().getName() + "$"};
	}

	public String getExpectedStatistics() {
		return STATISTICS;
	}

	public void doTest() {
		long time = System.currentTimeMillis();
		try {
			for (int i = 0; i < 10; i++) {
				System.out.print('.');
				ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(serializedData));
				ois.readObject();
				ois.close();
			}
			System.out.printf(" Test took %d ms\n", System.currentTimeMillis() - time);
		} catch (Exception e) {
			System.out.printf(" Test failed in %d ms\n", System.currentTimeMillis() - time);
			e.printStackTrace();
		}
	}

	private static class Entity implements Serializable {
		private final int value;
		
		private Entity(int value) {
			this.value = value;
		}
	}

	private static String STATISTICS = "" +
            "Allocated 16,000,000 bytes in 1,000,000 objects in 1 locations of 1 classes\n" +
            "-------------------------------------------------------------------------------\n" +
            "com.devexperts.aproftest.DeserializationTest$Entity: 16,000,000 (100%) bytes in 1,000,000 (100%) objects (avg size 16 bytes)\n" +
            "\tsun.reflect.GeneratedSerializationConstructorAccessor.newInstance: 16,000,000 (100%) bytes in 1,000,000 (100%) objects\n" +
            "\t\tjava.io.ObjectInputStream.readObject: 16,000,000 (100%) bytes in 1,000,000 (100%) objects\n" +
            "\t\t\tcom.devexperts.aproftest.DeserializationTest.doTest: 16,000,000 (100%) bytes in 1,000,000 (100%) objects\n" +
			"";
}
