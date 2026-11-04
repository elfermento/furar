// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.adapter.out.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gal.furar.facility.TestImages;
import gal.furar.facility.domain.ProcessedPhoto;
import gal.furar.shared.domain.DomainException;
import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.io.ByteArrayInputStream;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.junit.jupiter.api.Test;

/**
 * O procesado das fotos sen Spring nin base. Primeiro a imaxe xigante (plan, nota de D1-13): sae
 * antes de decodificala, sen reservar memoria.
 */
class ImageIoPhotoProcessorTest {

  final ImageIoPhotoProcessor processor = new ImageIoPhotoProcessor();

  @Test
  void aGiantPngIsRejectedFromItsHeaderWithoutDecodingIt() {
    byte[] giant = TestImages.giantPng(20_000, 20_000);
    assertThat(giant.length).isLessThan(8 * 1024 * 1024);
    com.sun.management.ThreadMXBean threads =
        (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
    long before = threads.getCurrentThreadAllocatedBytes();

    assertThatThrownBy(() -> processor.process(giant))
        .isInstanceOfSatisfying(
            DomainException.class,
            e -> assertThat(e.errorCode().code()).isEqualTo("facility.photo-too-many-pixels"));

    long allocated = threads.getCurrentThreadAllocatedBytes() - before;
    assertThat(allocated).as("bytes allocated").isLessThan(20L * 1024 * 1024);
  }

  @Test
  void aDeepPngUnderTheLimitsIsDecodedWithinTheMemoryBudget() throws Exception {
    // 6399 × 6250 = 40 Mpx en RGBA de 16 bits: 320 MB se se decodificase enteira.
    byte[] deep = TestImages.deepPng(6399, 6250);
    assertThat(deep.length).isLessThan(8 * 1024 * 1024);

    try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(deep))) {
      ImageReader reader = ImageIO.getImageReadersByFormatName("png").next();
      try {
        reader.setInput(in, true, true);
        assertThat(ImageIoPhotoProcessor.bytesPerPixel(reader)).isEqualTo(8);
        BufferedImage decoded = ImageIoPhotoProcessor.decode(reader, 6399, 6250);
        DataBuffer buffer = decoded.getRaster().getDataBuffer();
        long bytes =
            (long) buffer.getSize()
                * buffer.getNumBanks()
                * DataBuffer.getDataTypeSize(buffer.getDataType())
                / 8;
        assertThat(bytes)
            .as("decoded bytes")
            .isLessThanOrEqualTo(ImageIoPhotoProcessor.DECODE_BUDGET);
      } finally {
        reader.dispose();
      }
    }

    ProcessedPhoto photo = processor.process(deep);
    assertThat(Math.max(photo.width(), photo.height())).isLessThanOrEqualTo(1600);
  }

  @Test
  void subsamplingRoundsUpAndKeepsTheBudget() {
    assertThat(ImageIoPhotoProcessor.subsampling(3200, 2000, 4)).isEqualTo(1);
    assertThat(ImageIoPhotoProcessor.subsampling(3201, 2000, 4)).isEqualTo(2);
    assertThat(ImageIoPhotoProcessor.subsampling(6399, 6250, 3)).isEqualTo(2);
    int step = ImageIoPhotoProcessor.subsampling(6399, 6250, 8);
    assertThat(ImageIoPhotoProcessor.decodedBytes(6399, 6250, step, 8))
        .isLessThanOrEqualTo(ImageIoPhotoProcessor.DECODE_BUDGET);
  }

  @Test
  void exifGoesAwayGpsIncluded() {
    byte[] upload = TestImages.withExif(TestImages.halves(200, 100), 1);
    assertThat(TestImages.contains(upload, TestImages.GPS_MARK)).isTrue();

    ProcessedPhoto photo = processor.process(upload);

    for (byte[] out : new byte[][] {photo.large().bytes(), photo.thumb().bytes()}) {
      assertThat(TestImages.contains(out, "Exif".getBytes(StandardCharsets.US_ASCII))).isFalse();
      assertThat(TestImages.contains(out, TestImages.GPS_MARK)).isFalse();
      assertThat(ExifOrientation.read(out)).isEqualTo(1);
    }
    assertThat(photo.width()).isEqualTo(200);
    assertThat(photo.height()).isEqualTo(100);
    assertThat(photo.large().contentType()).isEqualTo("image/jpeg");
  }

  @Test
  void orientationSixIsTurnedClockwise() {
    byte[] upload = TestImages.withExif(TestImages.halves(200, 100), 6);
    assertThat(ExifOrientation.read(upload)).isEqualTo(6);

    ProcessedPhoto photo = processor.process(upload);

    assertThat(photo.width()).isEqualTo(100);
    assertThat(photo.height()).isEqualTo(200);
    BufferedImage large = TestImages.decode(photo.large().bytes());
    assertThat(large.getWidth()).isEqualTo(100);
    assertThat(large.getHeight()).isEqualTo(200);
    // A metade esquerda (vermella) queda arriba ao xirar 90° no sentido do reloxo.
    assertThat(TestImages.isRed(large.getRGB(50, 20))).isTrue();
    assertThat(TestImages.isRed(large.getRGB(50, 180))).isFalse();
  }

  @Test
  void everyOrientationGivesTheRightShape() {
    for (int o = 1; o <= 8; o++) {
      ProcessedPhoto photo = processor.process(TestImages.withExif(TestImages.halves(60, 30), o));
      boolean turned = o >= 5;
      assertThat(photo.width()).as("orientation " + o).isEqualTo(turned ? 30 : 60);
      assertThat(photo.height()).as("orientation " + o).isEqualTo(turned ? 60 : 30);
    }
  }

  @Test
  void bigPhotosShrinkTo1600AndThumbsTo480() {
    ProcessedPhoto photo = processor.process(TestImages.halves(4000, 3000));
    assertThat(photo.width()).isEqualTo(1600);
    assertThat(photo.height()).isEqualTo(1200);
    BufferedImage thumb = TestImages.decode(photo.thumb().bytes());
    assertThat(thumb.getWidth()).isEqualTo(480);
    assertThat(thumb.getHeight()).isEqualTo(360);
    assertThat(TestImages.isRed(thumb.getRGB(10, 180))).isTrue();
    assertThat(TestImages.isRed(thumb.getRGB(470, 180))).isFalse();
  }

  @Test
  void pngWithTransparencyBecomesJpegOnWhite() {
    ProcessedPhoto photo = processor.process(TestImages.png(300, 200));
    assertThat(photo.large().contentType()).isEqualTo("image/jpeg");
    BufferedImage large = TestImages.decode(photo.large().bytes());
    assertThat(large.getWidth()).isEqualTo(300);
    int bottom = large.getRGB(150, 190);
    assertThat(bottom & 0xFF).as("transparent half on white").isGreaterThan(240);
  }

  @Test
  void onlyJpegAndPngByContent() {
    assertThatThrownBy(() -> processor.process(TestImages.gif(10, 10)))
        .isInstanceOfSatisfying(
            DomainException.class,
            e -> assertThat(e.errorCode().code()).isEqualTo("facility.photo-unsupported-type"));
    assertThatThrownBy(() -> processor.process("<svg/>".getBytes(StandardCharsets.UTF_8)))
        .isInstanceOfSatisfying(
            DomainException.class,
            e -> assertThat(e.errorCode().code()).isEqualTo("facility.photo-unsupported-type"));
  }

  @Test
  void aBrokenJpegIsUnreadable() {
    byte[] jpeg = TestImages.halves(100, 100);
    byte[] cut = java.util.Arrays.copyOf(jpeg, 40);
    assertThatThrownBy(() -> processor.process(cut))
        .isInstanceOfSatisfying(
            DomainException.class,
            e -> assertThat(e.errorCode().code()).isEqualTo("facility.photo-unreadable"));
  }
}
