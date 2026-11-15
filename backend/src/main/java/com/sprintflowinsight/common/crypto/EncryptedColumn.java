package com.sprintflowinsight.common.crypto;

import java.util.regex.Pattern;

/**
 * A text column that holds {@link EncryptionService} envelopes, keyed by a numeric id column.
 * Feature modules declare one bean per encrypted column so that {@link ReEncryptionJob} can rotate
 * it. Names are SQL identifiers fixed in code, never user input; they are still validated because
 * they are concatenated into SQL.
 *
 * @param context the associated-data context the owning feature encrypts this column with
 */
public record EncryptedColumn(String table, String idColumn, String valueColumn, String context) {
  private static final Pattern IDENTIFIER = Pattern.compile("[a-z_][a-z0-9_]{0,62}");

  public EncryptedColumn {
    for (String identifier : new String[] {table, idColumn, valueColumn})
      if (identifier == null || !IDENTIFIER.matcher(identifier).matches())
        throw new IllegalArgumentException("Invalid SQL identifier for an encrypted column.");
    if (context == null || context.isBlank())
      throw new IllegalArgumentException("An encrypted column needs a non-blank context.");
  }

  /** The conventional context {@code table.column}. */
  public static EncryptedColumn of(String table, String idColumn, String valueColumn) {
    return new EncryptedColumn(table, idColumn, valueColumn, table + "." + valueColumn);
  }
}
