package com.sprintflowinsight.reporting;


import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.FloatRange;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

/** Word wrapping never overflows the line and never loses or reorders a visible character. */
class PdfWrapProperties {
  private static final PDType1Font FONT = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

  @Property(tries = 500)
  void everyLineFitsAndNoVisibleTextIsLost(
      @ForAll("text") String text, @ForAll @FloatRange(min = 60, max = 480) float width)
      throws Exception {
    List<String> lines = PdfReportWriter.wrap(text, FONT, 10.5f, width);
    for (String line : lines)
      assertThat(FONT.getStringWidth(line) / 1000 * 10.5f).isLessThanOrEqualTo(width);
    assertThat(String.join("", lines).replace(" ", "")).isEqualTo(text.replace(" ", ""));
  }

  @Provide
  Arbitrary<String> text() {
    Arbitrary<String> word =
        Arbitraries.strings()
            .withCharRange('a', 'z')
            .withChars('Å', 'ä', 'W', '—')
            .ofMinLength(1)
            .ofMaxLength(70);
    return word.list().ofMaxSize(40).map(words -> String.join(" ", words));
  }
}
