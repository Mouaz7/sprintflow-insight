package com.sprintflowinsight.common.error;

import java.sql.SQLTransientException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.TransactionSystemException;

public final class DatabaseFailures {
  private DatabaseFailures() {}

  public static final String DETAIL = "The database is temporarily unavailable. Try again shortly.";

  public static boolean isDatabaseUnavailable(Throwable failure) {
    for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
      if (cause instanceof DataAccessResourceFailureException
          || cause instanceof TransientDataAccessException
          || cause instanceof CannotCreateTransactionException
          || cause instanceof SQLTransientException
          || cause instanceof java.sql.SQLRecoverableException) return true;
      if (cause instanceof java.sql.SQLException sql
          && sql.getSQLState() != null
          && sql.getSQLState().startsWith("08")) return true;
      // HikariCP's proxy for a connection it already discarded (e.g. a rollback after the
      // network dropped) reports exactly this message.
      if (cause instanceof java.sql.SQLException sql
          && "Connection is closed".equals(sql.getMessage())) return true;
      if (cause instanceof TransactionSystemException transaction
          && transaction.getApplicationException() != null
          && isDatabaseUnavailable(transaction.getApplicationException())) return true;
      for (Throwable suppressed : cause.getSuppressed())
        if (isDatabaseUnavailable(suppressed)) return true;
    }
    return false;
  }
}
