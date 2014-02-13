package com.devexperts.aprof.hotspot;

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
