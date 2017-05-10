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

/**
 * Pool and index of Klass or Method tags that are written to 'hotspot.log' as a part of compilation task.
 *
 * @author Roman Elizarov
 */
@SuppressWarnings("unchecked")
class IdNamedIndex<T extends IdNamedObject> {
	private T[] pool = (T[])new IdNamedObject[16];
	private T[] index = (T[])new IdNamedObject[16];
	private int used;
	private int pooled;

	public T acquire() {
		if (pooled > used) {
			int index = --pooled;
			T result = pool[index];
			pool[index] = null;
			return result;
		}
		return null;
	}

	public void add(T obj) {
		if (pooled >= pool.length) {
			T[] newPool = (T[])new IdNamedObject[pooled * 2];
			System.arraycopy(pool, 0, newPool, 0, pooled);
			pool = newPool;
		}
		if (used < pooled) {
			T temp = pool[used];
			pool[used++] = obj;
			pool[pooled++] = temp;
		} else {
			assert used == pooled;
			pool[used++] = obj;
			pooled++;
		}
		if (obj.id >= index.length) {
			T[] newIndex = (T[])new IdNamedObject[obj.id + obj.id / 2];
			System.arraycopy(index, 0, newIndex, 0, index.length);
			index = newIndex;
		}
		index[obj.id] = obj;
	}

	public T get(int id) {
		return id < index.length ? index[id] : null;
	}

	public void releaseAll() {
		for (int i = 0; i < used; i++)
			index[pool[i].id] = null;
		used = 0;
	}
}
