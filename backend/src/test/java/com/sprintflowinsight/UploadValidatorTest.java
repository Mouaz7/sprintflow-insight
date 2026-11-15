package com.sprintflowinsight;

import static org.assertj.core.api.Assertions.*;

import com.sprintflowinsight.support.TestFiles;
import com.sprintflowinsight.workspace.attachment.UploadValidator;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class UploadValidatorTest {
  private final UploadValidator validator = new UploadValidator();

  @Test
  void detectsRealPdfAndPngWithoutTrustingTheFilename() throws Exception {
    assertThat(validator.validate(TestFiles.PDF)).isEqualTo(".pdf");
    var output = new java.io.ByteArrayOutputStream();
    javax.imageio.ImageIO.write(
        new java.awt.image.BufferedImage(2, 2, java.awt.image.BufferedImage.TYPE_INT_RGB),
        "png",
        output);
    assertThat(validator.validate(output.toByteArray())).isEqualTo(".png");
  }

  @Test
  void rejectsDisguisedTruncatedAndEmptyFiles() {
    for (byte[] bytes :
        new byte[][] {
          new byte[0],
          "<script>alert(1)</script>".getBytes(),
          "%PDF-1.4 fake".getBytes(),
          java.util.Arrays.copyOf(TestFiles.PDF, 32)
        })
      assertThatThrownBy(() -> validator.validate(bytes))
          .isInstanceOf(ResponseStatusException.class);
  }

  @Test
  void rejectsOversizedFiles() {
    assertThatThrownBy(() -> validator.validate(new byte[(int) UploadValidator.MAX_BYTES + 1]))
        .isInstanceOf(ResponseStatusException.class);
  }
}
