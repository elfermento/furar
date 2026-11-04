// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.adapter.out.image;

import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Orientación EXIF dun JPEG (etiqueta 0x0112 do IFD0) e o xiro que a aplica. ImageIO non a le nin a
 * aplica: sen isto, a foto dun móbil en vertical sae deitada. Lese dos bytes, sen bibliotecas: só o
 * segmento APP1 «Exif» e a primeira táboa TIFF. Calquera cousa rara vale 1 (tal como está).
 */
final class ExifOrientation {

  static final int NORMAL = 1;
  private static final byte[] EXIF = "Exif\0\0".getBytes(StandardCharsets.US_ASCII);
  private static final int TAG_ORIENTATION = 0x0112;
  private static final int TYPE_SHORT = 3;

  private ExifOrientation() {}

  /** 1–8 segundo EXIF; 1 se non hai EXIF ou non se entende. */
  static int read(byte[] jpeg) {
    int i = 2;
    while (i + 4 <= jpeg.length) {
      if ((jpeg[i] & 0xFF) != 0xFF) {
        return NORMAL;
      }
      int marker = jpeg[i + 1] & 0xFF;
      if (marker == 0xFF) {
        i++;
        continue;
      }
      if (marker == 0x01 || marker == 0xD8 || (marker >= 0xD0 && marker <= 0xD7)) {
        i += 2;
        continue;
      }
      if (marker == 0xDA || marker == 0xD9) {
        return NORMAL;
      }
      int length = u16(jpeg, i + 2, false);
      int end = i + 2 + length;
      if (length < 2 || end > jpeg.length) {
        return NORMAL;
      }
      if (marker == 0xE1
          && length >= 2 + EXIF.length + 8
          && Arrays.equals(jpeg, i + 4, i + 4 + EXIF.length, EXIF, 0, EXIF.length)) {
        return tiff(jpeg, i + 4 + EXIF.length, end);
      }
      i = end;
    }
    return NORMAL;
  }

  private static int tiff(byte[] b, int start, int end) {
    boolean little;
    if (b[start] == 'I' && b[start + 1] == 'I') {
      little = true;
    } else if (b[start] == 'M' && b[start + 1] == 'M') {
      little = false;
    } else {
      return NORMAL;
    }
    long ifd = start + u32(b, start + 4, little);
    if (ifd < start || ifd + 2 > end) {
      return NORMAL;
    }
    int count = u16(b, (int) ifd, little);
    for (int k = 0; k < count; k++) {
      long entry = ifd + 2 + 12L * k;
      if (entry + 12 > end) {
        return NORMAL;
      }
      int e = (int) entry;
      if (u16(b, e, little) == TAG_ORIENTATION) {
        int value = u16(b, e + 8, little);
        return u16(b, e + 2, little) == TYPE_SHORT && value >= 1 && value <= 8 ? value : NORMAL;
      }
    }
    return NORMAL;
  }

  /** A imaxe xa endereitada (2–8: espellos e xiros de 90°, 180° e 270°). */
  static BufferedImage apply(BufferedImage image, int orientation) {
    if (orientation <= NORMAL || orientation > 8) {
      return image;
    }
    int w = image.getWidth();
    int h = image.getHeight();
    boolean swap = orientation >= 5;
    int dw = swap ? h : w;
    int dh = swap ? w : h;
    int[] source = image.getRGB(0, 0, w, h, null, 0, w);
    int[] target = new int[source.length];
    for (int y = 0; y < h; y++) {
      for (int x = 0; x < w; x++) {
        int tx;
        int ty;
        switch (orientation) {
          case 2 -> {
            tx = w - 1 - x;
            ty = y;
          }
          case 3 -> {
            tx = w - 1 - x;
            ty = h - 1 - y;
          }
          case 4 -> {
            tx = x;
            ty = h - 1 - y;
          }
          case 5 -> {
            tx = y;
            ty = x;
          }
          case 6 -> {
            tx = h - 1 - y;
            ty = x;
          }
          case 7 -> {
            tx = h - 1 - y;
            ty = w - 1 - x;
          }
          default -> {
            tx = y;
            ty = w - 1 - x;
          }
        }
        target[ty * dw + tx] = source[y * w + x];
      }
    }
    BufferedImage result = new BufferedImage(dw, dh, BufferedImage.TYPE_INT_RGB);
    result.setRGB(0, 0, dw, dh, target, 0, dw);
    return result;
  }

  private static int u16(byte[] b, int at, boolean little) {
    int first = b[at] & 0xFF;
    int second = b[at + 1] & 0xFF;
    return little ? first | second << 8 : first << 8 | second;
  }

  private static long u32(byte[] b, int at, boolean little) {
    long high = u16(b, little ? at + 2 : at, little);
    long low = u16(b, little ? at : at + 2, little);
    return high << 16 | low;
  }
}
