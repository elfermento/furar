// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.out.qr;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import gal.furar.booking.domain.QrCode;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/**
 * ADR-0025, punto 13: o camiño SVG do QR, pintado tal como o pintaría a web (coa zona tranquila que
 * pon o CSS), le o lector de ZXing e dá o código da reserva.
 */
public class ZxingQrEncoderTest {

  private static final Pattern RUN = Pattern.compile("M(\\d+) (\\d+)h(\\d+)v1h-(\\d+)z");
  private static final int QUIET = 4;
  private static final int SCALE = 4;

  final ZxingQrEncoder encoder = new ZxingQrEncoder();

  @Test
  void thePathDecodesToTheBookingCode() throws Exception {
    QrCode qr = encoder.encode("FUR-AB23");

    assertThat(qr.size()).as("versión 1: 21 módulos, sen zona tranquila").isEqualTo(21);
    assertThat(decode(qr)).isEqualTo("FUR-AB23");
  }

  @Test
  void thePathIsOnlyHorizontalRunsInsideTheMatrix() {
    QrCode qr = encoder.encode("FUR-Z9Y8");
    String rest = RUN.matcher(qr.path()).replaceAll("");
    Matcher m = RUN.matcher(qr.path());
    boolean topLeft = false;
    while (m.find()) {
      int x = Integer.parseInt(m.group(1));
      int y = Integer.parseInt(m.group(2));
      int run = Integer.parseInt(m.group(3));
      assertThat(m.group(4)).isEqualTo(m.group(3));
      assertThat(x + run).isLessThanOrEqualTo(qr.size());
      assertThat(y).isLessThan(qr.size());
      topLeft |= x == 0 && y == 0 && run == 7;
    }
    assertThat(rest).as("nada máis ca tiras").isEmpty();
    assertThat(topLeft).as("o patrón de busca empeza no canto, sen marxe").isTrue();
  }

  @Test
  void thePngForTheMailDecodesToTheBookingCode() throws Exception {
    byte[] png = encoder.png("FUR-7K2Q", 8, 2);

    BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
    assertThat(image.getWidth()).as("(21 + 2 × 2) módulos de 8 px").isEqualTo(200);
    assertThat(image.getHeight()).isEqualTo(200);
    assertThat(image.getRGB(0, 0) & 0xFFFFFF).as("zona tranquila branca").isEqualTo(0xFFFFFF);
    assertThat(image.getRGB(16, 16) & 0xFFFFFF).as("patrón de busca").isZero();
    int[] pixels = image.getRGB(0, 0, 200, 200, null, 0, 200);
    BinaryBitmap bitmap =
        new BinaryBitmap(new HybridBinarizer(new RGBLuminanceSource(200, 200, pixels)));
    assertThat(
            new QRCodeReader()
                .decode(bitmap, Map.of(DecodeHintType.PURE_BARCODE, Boolean.TRUE))
                .getText())
        .isEqualTo("FUR-7K2Q");
  }

  /** Pinta o camiño en píxeles, coa marxe branca, e le o QR co lector de ZXing. */
  public static String decode(QrCode qr) throws Exception {
    int side = (qr.size() + 2 * QUIET) * SCALE;
    int[] pixels = new int[side * side];
    java.util.Arrays.fill(pixels, 0xFFFFFFFF);
    Matcher m = RUN.matcher(qr.path());
    while (m.find()) {
      int x = Integer.parseInt(m.group(1));
      int y = Integer.parseInt(m.group(2));
      int run = Integer.parseInt(m.group(3));
      for (int dy = 0; dy < SCALE; dy++) {
        for (int dx = 0; dx < run * SCALE; dx++) {
          pixels[((y + QUIET) * SCALE + dy) * side + (x + QUIET) * SCALE + dx] = 0xFF000000;
        }
      }
    }
    BinaryBitmap bitmap =
        new BinaryBitmap(new HybridBinarizer(new RGBLuminanceSource(side, side, pixels)));
    return new QRCodeReader()
        .decode(bitmap, Map.of(DecodeHintType.PURE_BARCODE, Boolean.TRUE))
        .getText();
  }
}
