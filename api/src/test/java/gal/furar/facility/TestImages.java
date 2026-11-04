// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import javax.imageio.ImageIO;

/**
 * Imaxes de proba feitas no momento (sen ficheiros binarios no repo): un JPEG coa metade esquerda
 * vermella e a dereita azul (para ver o xiro), o mesmo con EXIF (orientación e GPS), unha PNG
 * xigante que pesa poucos KB e un GIF.
 */
public final class TestImages {

  /** Latitude do GPS falso: 43° 29′ N (Narón). Os bytes buscan que non queden na saída. */
  public static final byte[] GPS_MARK = {0, 0, 0, 43, 0, 0, 0, 1, 0, 0, 0, 29, 0, 0, 0, 1};

  private TestImages() {}

  /** JPEG {@code width}×{@code height}: metade esquerda vermella, dereita azul. */
  public static byte[] halves(int width, int height) {
    BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = image.createGraphics();
    g.setColor(Color.RED);
    g.fillRect(0, 0, width / 2, height);
    g.setColor(Color.BLUE);
    g.fillRect(width / 2, 0, width - width / 2, height);
    g.dispose();
    return encode(image, "jpeg");
  }

  /** PNG con transparencia (canle alfa). */
  public static byte[] png(int width, int height) {
    BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = image.createGraphics();
    g.setColor(new Color(0, 128, 0, 200));
    g.fillRect(0, 0, width, height / 2);
    g.dispose();
    return encode(image, "png");
  }

  public static byte[] gif(int width, int height) {
    return encode(new BufferedImage(width, height, BufferedImage.TYPE_BYTE_INDEXED), "gif");
  }

  /**
   * O JPEG con APP1 «Exif» xusto despois do SOI: IFD0 coa orientación e un punteiro ao IFD GPS
   * (latitude N 43° 29′).
   */
  public static byte[] withExif(byte[] jpeg, int orientation) {
    ByteBuffer tiff = ByteBuffer.allocate(92);
    tiff.put((byte) 'M').put((byte) 'M').putShort((short) 42).putInt(8);
    // IFD0 en 8: dúas entradas (orientación e GPS) e fin.
    tiff.putShort((short) 2);
    tiff.putShort((short) 0x0112).putShort((short) 3).putInt(1);
    tiff.putShort((short) orientation).putShort((short) 0);
    tiff.putShort((short) 0x8825).putShort((short) 4).putInt(1).putInt(38);
    tiff.putInt(0);
    // IFD GPS en 38: referencia N e latitude (tres racionais en 68).
    tiff.putShort((short) 2);
    tiff.putShort((short) 0x0001).putShort((short) 2).putInt(2).put((byte) 'N').put(new byte[3]);
    tiff.putShort((short) 0x0002).putShort((short) 5).putInt(3).putInt(68);
    tiff.putInt(0);
    tiff.put(GPS_MARK).putInt(0).putInt(1);
    byte[] exif = "Exif\0\0".getBytes(StandardCharsets.US_ASCII);
    int length = 2 + exif.length + tiff.capacity();
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    out.write(jpeg, 0, 2);
    out.write(0xFF);
    out.write(0xE1);
    out.write(length >> 8);
    out.write(length & 0xFF);
    out.writeBytes(exif);
    out.writeBytes(tiff.array());
    out.write(jpeg, 2, jpeg.length - 2);
    return out.toByteArray();
  }

  /**
   * PNG en grises de {@code width}×{@code height} toda negra, escrita a man sen ter a imaxe en
   * memoria: 20000×20000 son 400 MB decodificada e uns 400 KB no ficheiro.
   */
  public static byte[] giantPng(int width, int height) {
    return blankPng(width, height, 8, 0, 1);
  }

  /**
   * PNG RGBA de 16 bits en branco (8 bytes por píxel decodificada): pouco peso e moita memoria ao
   * decodificala enteira.
   */
  public static byte[] deepPng(int width, int height) {
    return blankPng(width, height, 16, 6, 8);
  }

  private static byte[] blankPng(
      int width, int height, int bitDepth, int colorType, int bytesPerPixel) {
    try {
      ByteArrayOutputStream file = new ByteArrayOutputStream();
      file.write(new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'});
      ByteBuffer header = ByteBuffer.allocate(13);
      header
          .putInt(width)
          .putInt(height)
          .put((byte) bitDepth)
          .put((byte) colorType)
          .put(new byte[3]);
      chunk(file, "IHDR", header.array());
      ByteArrayOutputStream idat = new ByteArrayOutputStream();
      Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);
      try (DeflaterOutputStream z = new DeflaterOutputStream(idat, deflater, 1 << 16)) {
        byte[] row = new byte[width * bytesPerPixel + 1];
        for (int y = 0; y < height; y++) {
          z.write(row);
        }
      } finally {
        deflater.end();
      }
      chunk(file, "IDAT", idat.toByteArray());
      chunk(file, "IEND", new byte[0]);
      return file.toByteArray();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** ¿Aparece {@code needle} nalgún sitio de {@code haystack}? */
  public static boolean contains(byte[] haystack, byte[] needle) {
    outer:
    for (int i = 0; i + needle.length <= haystack.length; i++) {
      for (int j = 0; j < needle.length; j++) {
        if (haystack[i + j] != needle[j]) {
          continue outer;
        }
      }
      return true;
    }
    return false;
  }

  public static BufferedImage decode(byte[] bytes) {
    try {
      return ImageIO.read(new java.io.ByteArrayInputStream(bytes));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** ¿Predomina o vermello (true) ou o azul (false) no píxel? */
  public static boolean isRed(int rgb) {
    return ((rgb >> 16) & 0xFF) > (rgb & 0xFF);
  }

  private static void chunk(ByteArrayOutputStream out, String type, byte[] data)
      throws IOException {
    DataOutputStream d = new DataOutputStream(out);
    byte[] name = type.getBytes(StandardCharsets.US_ASCII);
    d.writeInt(data.length);
    d.write(name);
    d.write(data);
    CRC32 crc = new CRC32();
    crc.update(name);
    crc.update(data);
    d.writeInt((int) crc.getValue());
  }

  private static byte[] encode(BufferedImage image, String format) {
    try {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      if (!ImageIO.write(image, format, out)) {
        throw new IllegalStateException("no writer for " + format);
      }
      return out.toByteArray();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
