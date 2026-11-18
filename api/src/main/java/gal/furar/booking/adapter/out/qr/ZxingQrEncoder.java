// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.out.qr;

import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.google.zxing.qrcode.encoder.ByteMatrix;
import com.google.zxing.qrcode.encoder.Encoder;
import gal.furar.booking.application.port.out.QrEncoder;
import gal.furar.booking.domain.QrCode;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import javax.imageio.ImageIO;
import org.springframework.stereotype.Component;

/**
 * QR con ZXing {@code core} (ADR-0025, punto 13): a matriz de módulos, sen zona tranquila, como un
 * camiño SVG cunha tira por cada serie de módulos escuros seguidos dunha fila ({@code M x y h n v1
 * h-n z}), ou en PNG para o correo. Corrección de erros M (15 %): chega para unha pantalla ou un
 * papel con algún risco.
 */
@Component
class ZxingQrEncoder implements QrEncoder {

  private static final Map<EncodeHintType, Object> HINTS =
      Map.of(EncodeHintType.CHARACTER_SET, StandardCharsets.UTF_8.name());

  @Override
  public QrCode encode(String text) {
    ByteMatrix matrix = matrix(text);
    StringBuilder path = new StringBuilder();
    for (int y = 0; y < matrix.getHeight(); y++) {
      int x = 0;
      while (x < matrix.getWidth()) {
        if (matrix.get(x, y) != 1) {
          x++;
          continue;
        }
        int from = x;
        while (x < matrix.getWidth() && matrix.get(x, y) == 1) {
          x++;
        }
        int run = x - from;
        path.append('M')
            .append(from)
            .append(' ')
            .append(y)
            .append('h')
            .append(run)
            .append("v1h-")
            .append(run)
            .append('z');
      }
    }
    return new QrCode(matrix.getWidth(), path.toString());
  }

  @Override
  public byte[] png(String text, int modulePixels, int quietModules) {
    if (modulePixels < 1 || quietModules < 0) {
      throw new IllegalArgumentException("Invalid QR image size");
    }
    ByteMatrix matrix = matrix(text);
    int side = (matrix.getWidth() + 2 * quietModules) * modulePixels;
    BufferedImage image = new BufferedImage(side, side, BufferedImage.TYPE_BYTE_BINARY);
    for (int py = 0; py < side; py++) {
      int y = py / modulePixels - quietModules;
      for (int px = 0; px < side; px++) {
        int x = px / modulePixels - quietModules;
        boolean dark =
            x >= 0
                && y >= 0
                && x < matrix.getWidth()
                && y < matrix.getHeight()
                && matrix.get(x, y) == 1;
        image.setRGB(px, py, dark ? 0xFF000000 : 0xFFFFFFFF);
      }
    }
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try {
      if (!ImageIO.write(image, "png", out)) {
        throw new IllegalStateException("No PNG writer");
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return out.toByteArray();
  }

  private static ByteMatrix matrix(String text) {
    try {
      return Encoder.encode(text, ErrorCorrectionLevel.M, HINTS).getMatrix();
    } catch (WriterException e) {
      throw new IllegalArgumentException("Cannot encode a QR code for: " + text, e);
    }
  }
}
