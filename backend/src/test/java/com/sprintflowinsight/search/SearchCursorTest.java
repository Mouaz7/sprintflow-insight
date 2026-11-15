package com.sprintflowinsight.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sprintflowinsight.common.error.ApiException;
import com.sprintflowinsight.common.error.ErrorCode;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class SearchCursorTest {
  private static String encoded(String plain) {
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(plain.getBytes(StandardCharsets.US_ASCII));
  }

  @Test
  void roundTripsAndStaysUrlSafe() {
    SearchCursor cursor = new SearchCursor(new BigDecimal("0.607927"), 5, 42);
    String text = cursor.encode();
    assertThat(text).matches("[A-Za-z0-9_-]+").hasSizeLessThanOrEqualTo(SearchCursor.MAX_LENGTH);
    assertThat(SearchCursor.decode(text)).isEqualTo(cursor);
    assertThat(SearchCursor.decode(encoded("1000.000000:1:9"))).isEqualTo(
        new SearchCursor(new BigDecimal("1000.000000"), 1, 9));
  }

  @Test
  void encodesTheRankWithSixDecimals() {
    assertThat(
            new String(
                Base64.getUrlDecoder().decode(new SearchCursor(BigDecimal.ONE, 3, 7).encode()),
                StandardCharsets.US_ASCII))
        .isEqualTo("1.000000:3:7");
  }

  @Test
  void rejectsMalformedCursorsAsInvalidParameters() {
    for (String bad :
        new String[] {
          "!!!",
          encoded(""),
          encoded("0.5:5:1"),
          encoded("0.500000:6:1"),
          encoded("0.500000:0:1"),
          encoded("0.500000:5:0"),
          encoded("-1.000000:5:1"),
          encoded("01.000000:5:1"),
          encoded("10000.000000:5:1"),
          encoded("0.500000:5:99999999999999999999"),
          encoded("0.500000:5:1:extra")
        })
      assertThatThrownBy(() -> SearchCursor.decode(bad))
          .as(bad)
          .isInstanceOfSatisfying(
              ApiException.class,
              problem -> assertThat(problem.code()).isEqualTo(ErrorCode.INVALID_PARAMETER));
  }
}
