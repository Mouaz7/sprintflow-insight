package com.sprintflowinsight.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.imageio.ImageIO;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdfparser.PDFParser;
import org.apache.tika.Tika;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class UploadValidator {
  public static final long MAX_BYTES = 15L * 1024 * 1024;
  private static final long MAX_PIXELS = 20_000_000;
  private static final Map<String, String> EXTENSIONS =
      Map.of(
          "image/jpeg",
          ".jpg",
          "image/png",
          ".png",
          "image/gif",
          ".gif",
          "image/webp",
          ".webp",
          "application/pdf",
          ".pdf");
  private final java.util.concurrent.Semaphore admission = new java.util.concurrent.Semaphore(2);
  private final Tika detector = new Tika();

  public String validate(byte[] bytes) {
    if (!admission.tryAcquire())
      throw new ResponseStatusException(
          HttpStatus.SERVICE_UNAVAILABLE, "File checks are busy. Please try again.");
    try {
      return validateContent(bytes);
    } finally {
      admission.release();
    }
  }

  private String validateContent(byte[] bytes) {
    if (bytes.length == 0 || bytes.length > MAX_BYTES)
      throw invalid("File must be between 1 byte and 15 MB.");
    String type = detector.detect(bytes);
    String extension = EXTENSIONS.get(type);
    if (extension == null) throw invalid("Choose a valid JPG, PNG, GIF, WebP or PDF file.");
    try {
      if (type.equals("application/pdf")) validatePdf(bytes);
      else validateImage(bytes);
    } catch (IOException | RuntimeException error) {
      if (error instanceof ResponseStatusException status) throw status;
      throw invalid("The file is damaged or exceeds the supported document limits.");
    }
    return extension;
  }

  private void validateImage(byte[] bytes) throws IOException {
    try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
      var readers = ImageIO.getImageReaders(input);
      if (!readers.hasNext()) throw invalid("The image format could not be read.");
      var reader = readers.next();
      try {
        AtomicBoolean warning = new AtomicBoolean();
        reader.addIIOReadWarningListener((source, message) -> warning.set(true));
        reader.setInput(input, false, true);
        int frames = reader.getNumImages(true);
        if (frames < 1 || frames > 100) throw invalid("Images may contain at most 100 frames.");
        long totalPixels = 0;
        for (int frame = 0; frame < frames; frame++) {
          int width = reader.getWidth(frame), height = reader.getHeight(frame);
          long pixels = (long) width * height;
          totalPixels += pixels;
          if (width < 1 || height < 1 || pixels > MAX_PIXELS || totalPixels > 40_000_000)
            throw invalid(
                "Images may contain at most 20 megapixels per frame and 40 megapixels in total.");
          var image = reader.read(frame);
          if (image == null || warning.get()) throw invalid("The image is damaged or incomplete.");
          image.flush();
        }
      } finally {
        reader.dispose();
      }
    }
  }

  private void validatePdf(byte[] bytes) throws IOException {
    try (var source = new RandomAccessReadBuffer(bytes)) {
      var parser = new PDFParser(source);
      try (var document = parser.parse(false)) {
        if (document.isEncrypted()
            || document.getNumberOfPages() < 1
            || document.getNumberOfPages() > 500)
          throw invalid("PDFs must be unencrypted and contain between 1 and 500 pages.");
        long total = 0;
        for (var page : document.getPages()) {
          try (var content = page.getContents()) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = content.read(buffer)) != -1) {
              total += count;
              if (total > 32L * 1024 * 1024)
                throw invalid("The PDF expands beyond the supported document limit.");
            }
          }
        }
      }
    }
  }

  private ResponseStatusException invalid(String message) {
    return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
  }
}
