package com.devexperts.aprof.util;

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

import java.util.Comparator;

/**
 * @author Dmitry Paraschenko
 */
public class QuickSort {
	public static <V> void sort(V[] items, int l, int r, Comparator<V> comparator) {
		if (l < r) {
			qsort(items, l, r - 1, comparator);
		}
	}

	private static <V> void qsort(V[] items, int l, int r, Comparator<V> comparator) {
		// non-recursive for largest range
		while (r - l > 4) {
			V x = items[(l + r) >> 1];
			int i = l;
			int j = r;
			do {
				while (comparator.compare(items[i], x) < 0)
					i++;
				while (comparator.compare(items[j], x) > 0)
					j--;
				if (i <= j) {
					V t = items[i];
					items[i] = items[j];
					items[j] = t;
					i++;
					j--;
				} else {
					break;
				}
			} while (i <= j);
			// now we need to sort [l,j] and [i,r]
			if (j - l < r - i) {
				qsort(items, l, j, comparator);
				l = i;
			} else {
				qsort(items, i, r, comparator);
				r = j;
			}
		}
		// simple n^2 sort for the rest
		for (int i = l; i < r; i++) {
			for (int j = i + 1; j <= r; j++) {
				if (comparator.compare(items[i], items[j]) > 0) {
					V t = items[i];
					items[i] = items[j];
					items[j] = t;
				}
			}
		}
	}
}
