package com.sprintflowinsight.workspace.transfer;

import com.sprintflowinsight.common.error.ApiException;
import java.util.*;

/** Bounded RFC4180 reader. Quotes, separators and newlines never reach SQL or a shell. */
public final class CsvImportParser {
  public static final int MAX_ROWS = 200,
      MAX_COLUMNS = 64,
      MAX_CELL = 8192,
      MAX_CONTENT = 1_000_000;

  private CsvImportParser() {}

  public static ImportTable parse(String input) {
    if (input == null || input.length() > MAX_CONTENT) throw bad("Use a CSV file of at most 1 MB.");
    if (input.startsWith("\ufeff")) input = input.substring(1);
    if (input.indexOf('\0') >= 0) throw bad("CSV cannot contain null characters.");
    var records = new ArrayList<List<String>>();
    var row = new ArrayList<String>();
    var cell = new StringBuilder();
    boolean quoted = false, closed = false, ended = true;
    for (int i = 0; i < input.length(); i++) {
      char c = input.charAt(i);
      ended = false;
      if (quoted) {
        if (c == '"') {
          if (i + 1 < input.length() && input.charAt(i + 1) == '"') {
            cell.append('"');
            i++;
          } else {
            quoted = false;
            closed = true;
          }
        } else cell.append(c);
      } else if (c == ',' || c == '\r' || c == '\n') {
        addCell(row, cell);
        closed = false;
        if (c != ',') {
          if (c == '\r' && i + 1 < input.length() && input.charAt(i + 1) == '\n') i++;
          addRow(records, row);
          row = new ArrayList<>();
          ended = true;
        }
      } else if (c == '"') {
        if (closed || !cell.isEmpty())
          throw bad("A quote must start a CSV field; escape quotes by doubling them.");
        quoted = true;
      } else {
        if (closed) throw bad("Unexpected text after a closing CSV quote.");
        cell.append(c);
      }
      if (cell.length() > MAX_CELL) throw bad("A CSV cell can have at most 8192 characters.");
    }
    if (quoted) throw bad("A CSV quote was not closed.");
    if (!ended || !row.isEmpty() || !cell.isEmpty()) {
      addCell(row, cell);
      addRow(records, row);
    }
    if (records.isEmpty()) throw bad("CSV needs a header and at least one data row.");
    var headers = records.removeFirst().stream().map(String::strip).toList();
    if (headers.stream().anyMatch(h -> h.isEmpty() || h.length() > 160)
        || new HashSet<>(headers).size() != headers.size())
      throw bad("CSV headers must be non-empty, unique and at most 160 characters.");
    var rows = new ArrayList<Map<String, String>>();
    for (var values : records) {
      if (values.size() != headers.size())
        throw bad("Each CSV record must have the same number of fields as its header.");
      var mapped = new LinkedHashMap<String, String>();
      for (int j = 0; j < headers.size(); j++) mapped.put(headers.get(j), values.get(j));
      rows.add(mapped);
    }
    if (rows.isEmpty()) throw bad("CSV needs at least one data row.");
    return new ImportTable(headers, rows);
  }

  private static void addCell(List<String> row, StringBuilder cell) {
    if (row.size() >= MAX_COLUMNS) throw bad("At most 64 CSV columns are supported.");
    row.add(cell.toString());
    cell.setLength(0);
  }

  private static void addRow(List<List<String>> rows, List<String> row) {
    if (row.stream().allMatch(String::isEmpty)) return;
    if (rows.size() > MAX_ROWS) throw bad("Import at most 200 tasks per batch.");
    rows.add(row);
  }

  private static ApiException bad(String s) {
    return ApiException.badRequest(s);
  }
}
