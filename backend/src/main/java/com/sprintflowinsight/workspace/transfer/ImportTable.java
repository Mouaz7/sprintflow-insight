package com.sprintflowinsight.workspace.transfer;

import java.util.*;

public record ImportTable(List<String> columns, List<Map<String, String>> rows) {
  public ImportTable {
    columns = List.copyOf(columns);
    rows = rows.stream().map(Map::copyOf).toList();
  }
}
