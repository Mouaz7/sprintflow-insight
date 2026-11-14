package com.sprintflowinsight.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.sprintflowinsight.service.PdfReportWriter.Line;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

class PdfReportWriterTest {
  private final PdfReportWriter writer = new PdfReportWriter();

  @Test
  void rendersAValidMultiPageDocumentWithFootersAndMetadata() throws Exception {
    List<Line> lines = new ArrayList<>(List.of(Line.title("Report"), Line.heading("TASKS")));
    for (int i = 0; i < 120; i++) lines.add(Line.body("Task " + i + " — Done — 3 pts"));
    byte[] pdf = writer.render("Weekly report", lines);

    try (var document = Loader.loadPDF(pdf)) {
      assertThat(document.getNumberOfPages()).isGreaterThan(1);
      assertThat(document.getDocumentInformation().getTitle()).isEqualTo("Weekly report");
      String text = new PDFTextStripper().getText(document);
      assertThat(text).contains("Task 0 — Done", "Task 119 — Done", "Page 1 of " + document.getNumberOfPages());
    }
  }

  @Test
  void swedishTextSurvivesAndUnsupportedCharactersDegradeSafely() throws Exception {
    byte[] pdf = writer.render("Rapport", List.of(Line.body("Åtgärda bugg i användarflödet 🚀 漢字")));
    try (var document = Loader.loadPDF(pdf)) {
      assertThat(new PDFTextStripper().getText(document)).contains("Åtgärda bugg i användarflödet ? ??");
    }
  }

  @Test
  void wrapsByRenderedWidthAndSplitsOverlongWords() throws Exception {
    var font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    List<String> wrapped =
        PdfReportWriter.wrap("word ".repeat(40).trim() + " " + "x".repeat(300), font, 10.5f, 400);
    assertThat(wrapped).hasSizeGreaterThan(3);
    for (String line : wrapped) assertThat(font.getStringWidth(line) / 1000 * 10.5f).isLessThanOrEqualTo(400);
    assertThat(String.join("", wrapped).replace(" ", "")).isEqualTo("word".repeat(40) + "x".repeat(300));
  }
}
