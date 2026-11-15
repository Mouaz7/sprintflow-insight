package com.sprintflowinsight.search;

import com.sprintflowinsight.common.error.ApiException;
import com.sprintflowinsight.common.error.ErrorCode;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Keyset position after the last hit of a page: its rank (six decimals, as rounded in SQL), type
 * order and id. Encoded as base64url so clients treat it as opaque. A forged cursor only moves the
 * window; access filtering is part of the same SQL statement and cannot be bypassed.
 */
public record SearchCursor(BigDecimal rank, int typeOrder, long id) {
  public static final int MAX_LENGTH = 100;
  private static final Pattern FORMAT =
      Pattern.compile("(0|[1-9][0-9]{0,3})\\.([0-9]{6}):([1-5]):([1-9][0-9]{0,18})");

  public String encode() {
    String plain = rank.setScale(6, java.math.RoundingMode.UNNECESSARY).toPlainString()
        + ":" + typeOrder + ":" + id;
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(plain.getBytes(StandardCharsets.US_ASCII));
  }

  /** Decodes a client cursor; anything malformed is a 400 {@code INVALID_PARAMETER}. */
  public static SearchCursor decode(String text) {
    String plain;
    try {
      plain = new String(Base64.getUrlDecoder().decode(text), StandardCharsets.US_ASCII);
    } catch (IllegalArgumentException malformed) {
      throw invalid();
    }
    Matcher match = FORMAT.matcher(plain);
    if (!match.matches()) throw invalid();
    try {
      return new SearchCursor(
          new BigDecimal(match.group(1) + "." + match.group(2)),
          Integer.parseInt(match.group(3)),
          Long.parseLong(match.group(4)));
    } catch (NumberFormatException overflow) {
      throw invalid();
    }
  }

  private static ApiException invalid() {
    return new ApiException(ErrorCode.INVALID_PARAMETER, "The search cursor is not valid.");
  }
}
