package com.sprintflowinsight;

final class TestFiles {
  static final byte[] PDF = pdf();

  private TestFiles() {}

  private static byte[] pdf() {
    try (var document = new org.apache.pdfbox.pdmodel.PDDocument();
        var output = new java.io.ByteArrayOutputStream()) {
      document.addPage(new org.apache.pdfbox.pdmodel.PDPage());
      document.save(output);
      return output.toByteArray();
    } catch (java.io.IOException error) {
      throw new java.io.UncheckedIOException(error);
    }
  }
}
