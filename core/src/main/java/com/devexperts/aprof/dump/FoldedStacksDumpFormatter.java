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

import com.devexperts.aprof.Configuration;

import java.io.PrintWriter;

public class FoldedStacksDumpFormatter implements DumpFormatter {

  private final ThreadLocal<StringBuilder> buffer = new ThreadLocal<StringBuilder>() {
    @Override
    protected StringBuilder initialValue() {
      return new StringBuilder(1024);
    }
  };

  private final double threshold;
  private final boolean collectSizes;
  private final boolean skipTotal;


  public FoldedStacksDumpFormatter(Configuration config) {
    threshold = config.getThreshold();
    collectSizes = config.isSize();
    skipTotal = config.getFilecount() > 1;
  }


  public void dumpSnapshot(PrintWriter out, SnapshotRoot root, String kind) {
    if (skipTotal && "TOTAL".equals(kind))
      return;

    StringBuilder sb = buffer.get();
    sb.setLength(0);

    printNode(out, root, sb, root, 0, kind);
  }

  public long dumpReportHeader(PrintWriter out, long now, long start, String argsStr, int snapshotCount, int overflowCount) {
    return 0;
  }

  private long getSize(SnapshotDeep node) {
    return collectSizes ? node.getSize() : node.getCount();
  }

  private long printNode(PrintWriter out, SnapshotDeep node, StringBuilder prefix, SnapshotRoot root, int depth, String rootName) {
    if (node.isEmpty() || !node.exceedsThreshold(root, threshold))
      return 0;

    int prefixLen = prefix.length();
    try {
      String suffix = "_[j]";
      if (depth == 1) {
        suffix = "_[k]"; // it's a type
      }
      if (node.isPossiblyEliminatedAllocation()) {
        suffix = "_[i]"; //todo: figure out whether it's top or last node
      }
      //todo: make sure <unknown> is marked in red

      String nodeName = depth == 0 ? rootName : node.getName();
      if (depth == 0 || SnapshotDeep.UNKNOWN.equals(nodeName))
        suffix = "";

      if (nodeName != null)
        prefix.append(nodeName).append(suffix).append(';');

      int ccnt = node.getUsed();
      long childrenSize = 0;
      for (int i = 0; i < ccnt; i++) {
        SnapshotDeep child = node.getChild(i);
        childrenSize += printNode(out, child, prefix, root, depth + 1, rootName);
      }

      long exclusiveSize = getSize(node) - childrenSize;
      if (exclusiveSize > 0 && nodeName != null) {
        prefix.setLength(prefixLen);
        out.print(prefix);
        out.print(nodeName);
        out.print(suffix);
        out.print(' ');
        out.println(exclusiveSize);
      }

      return getSize(node);

    } finally {
      prefix.setLength(prefixLen);
    }
  }
}
