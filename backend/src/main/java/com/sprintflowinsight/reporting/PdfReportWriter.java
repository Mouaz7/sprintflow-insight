package com.sprintflowinsight.reporting;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Component;

/**
 * Renders a simple text report (headings + lines) as a proper PDF with PDFBox. Lines wrap by
 * measured glyph width rather than character count, pages carry "Page x of y" footers, and
 * characters the standard fonts cannot encode degrade to "?" instead of corrupting the file.
 */
@Component
public class PdfReportWriter {
  private static final PDRectangle PAGE = PDRectangle.A4;
  private static final float MARGIN = 56;
  private static final float BODY_SIZE = 10.5f;
  private static final float HEADING_SIZE = 12.5f;
  private static final float TITLE_SIZE = 18;
  private static final float LEADING = 1.45f;

  /** One printable line; headings are set in bold with extra space above. */
  public record Line(String text, Kind kind) {
    public enum Kind {
      TITLE,
      HEADING,
      BODY,
      BLANK
    }

    public static Line title(String text) {
      return new Line(text, Kind.TITLE);
    }

    public static Line heading(String text) {
      return new Line(text, Kind.HEADING);
    }

    public static Line body(String text) {
      return new Line(text, Kind.BODY);
    }

    public static Line blank() {
      return new Line("", Kind.BLANK);
    }
  }

  public byte[] render(String documentTitle, List<Line> lines) throws IOException {
    try (PDDocument document = new PDDocument()) {
      PDFont regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
      PDFont bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
      float width = PAGE.getWidth() - 2 * MARGIN;

      List<List<Placed>> pages = paginate(layout(lines, regular, bold, width));
      for (int index = 0; index < pages.size(); index++) {
        PDPage page = new PDPage(PAGE);
        document.addPage(page);
        try (PDPageContentStream content = new PDPageContentStream(document, page)) {
          for (Placed placed : pages.get(index)) {
            content.beginText();
            content.setFont(placed.font(), placed.size());
            content.newLineAtOffset(MARGIN, placed.y());
            content.showText(placed.text());
            content.endText();
          }
          String footer = "Page " + (index + 1) + " of " + pages.size();
          content.beginText();
          content.setFont(regular, 8.5f);
          float footerWidth = regular.getStringWidth(footer) / 1000 * 8.5f;
          content.newLineAtOffset(PAGE.getWidth() - MARGIN - footerWidth, MARGIN / 2);
          content.showText(footer);
          content.endText();
        }
      }

      PDDocumentInformation info = document.getDocumentInformation();
      info.setTitle(documentTitle);
      info.setCreator("SprintFlow Insight");
      info.setCreationDate(Calendar.getInstance());

      ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      document.save(bytes);
      return bytes.toByteArray();
    }
  }

  private record Placed(String text, PDFont font, float size, float y) {}

  private record Measured(String text, PDFont font, float size, float spaceBefore) {}

  private static List<Measured> layout(List<Line> lines, PDFont regular, PDFont bold, float width)
      throws IOException {
    List<Measured> measured = new ArrayList<>();
    for (Line line : lines) {
      PDFont font =
          line.kind() == Line.Kind.BODY || line.kind() == Line.Kind.BLANK ? regular : bold;
      float size =
          switch (line.kind()) {
            case TITLE -> TITLE_SIZE;
            case HEADING -> HEADING_SIZE;
            default -> BODY_SIZE;
          };
      float spaceBefore = line.kind() == Line.Kind.HEADING ? size * 0.6f : 0;
      boolean first = true;
      for (String part : wrap(encodable(line.text(), font), font, size, width)) {
        measured.add(new Measured(part, font, size, first ? spaceBefore : 0));
        first = false;
      }
    }
    return measured;
  }

  private static List<List<Placed>> paginate(List<Measured> measured) {
    List<List<Placed>> pages = new ArrayList<>();
    List<Placed> current = new ArrayList<>();
    float top = PAGE.getHeight() - MARGIN;
    float y = top;
    for (Measured line : measured) {
      float advance = line.spaceBefore() + line.size() * LEADING;
      if (y - advance < MARGIN && !current.isEmpty()) {
        pages.add(current);
        current = new ArrayList<>();
        y = top;
      }
      y -= advance;
      current.add(new Placed(line.text(), line.font(), line.size(), y));
    }
    if (!current.isEmpty() || pages.isEmpty()) pages.add(current);
    return pages;
  }

  /** Greedy word wrap by rendered width; words longer than a line are split. */
  static List<String> wrap(String text, PDFont font, float size, float width) throws IOException {
    List<String> result = new ArrayList<>();
    if (text.isEmpty()) {
      result.add("");
      return result;
    }
    StringBuilder line = new StringBuilder();
    for (String word : text.split(" ", -1)) {
      String candidate = line.isEmpty() ? word : line + " " + word;
      if (widthOf(candidate, font, size) <= width) {
        line.setLength(0);
        line.append(candidate);
        continue;
      }
      if (!line.isEmpty()) result.add(line.toString());
      line.setLength(0);
      String rest = word;
      while (widthOf(rest, font, size) > width) {
        int cut = rest.length() - 1;
        while (cut > 1 && widthOf(rest.substring(0, cut), font, size) > width) cut--;
        result.add(rest.substring(0, cut));
        rest = rest.substring(cut);
      }
      line.append(rest);
    }
    result.add(line.toString());
    return result;
  }

  private static float widthOf(String text, PDFont font, float size) throws IOException {
    return font.getStringWidth(text) / 1000 * size;
  }

  /** Replaces characters the font cannot encode (e.g. emoji, CJK) with "?". */
  static String encodable(String text, PDFont font) {
    StringBuilder result = new StringBuilder(text.length());
    text.codePoints()
        .forEach(
            codePoint -> {
              String character = new String(Character.toChars(codePoint));
              if (Character.isISOControl(codePoint)) {
                result.append(' ');
                return;
              }
              try {
                font.encode(character);
                result.append(character);
              } catch (IllegalArgumentException | IOException unsupported) {
                result.append('?');
              }
            });
    return result.toString();
  }
}
