package com.devexperts.aprof.dump;

/*-
 * #%L
 * Aprof Core
 * %%
 * Copyright (C) 2002 - 2020 Devexperts, LLC
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

import java.io.PrintWriter;

public interface DumpFormatter {
  void dumpSnapshot(PrintWriter out, SnapshotRoot ss, String kind);

  long dumpReportHeader(PrintWriter out, long now, long start, String argsStr, int snapshotCount, int overflowCount);
}
