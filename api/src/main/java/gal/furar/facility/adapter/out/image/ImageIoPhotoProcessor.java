// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.adapter.out.image;

import gal.furar.facility.application.port.out.PhotoProcessor;
import gal.furar.facility.domain.FacilityError;
import gal.furar.facility.domain.PhotoFile;
import gal.furar.facility.domain.PhotoVariant;
import gal.furar.facility.domain.ProcessedPhoto;
import gal.furar.shared.domain.DomainException;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.Semaphore;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import org.springframework.stereotype.Component;

/**
 * Fotos con ImageIO do JDK, sen dependencias (plan do dominio 1, §1.2.9). Por orde: tipo polos
 * primeiros bytes (só JPEG e PNG); ancho e alto da cabeceira, **antes de decodificar** (máis de
 * {@value #MAX_PIXELS} píxeles, fóra: unha PNG de 20000×20000 pesa poucos KB e decodificada
 * ocuparía GB); decodificación con submostraxe cando a imaxe é moito máis grande ca 1600 px; xiro
 * segundo a orientación EXIF; e reencodado a JPEG sen metadatos (GPS incluído), a grande e a
 * miniatura. Como moito {@value #CONCURRENT} decodificacións á vez: o resto agarda.
 *
 * <p>Sempre JPEG á saída: unha foto dunha instalación non precisa transparencia (o fondo queda
 * branco) e en PNG pesaría varias veces máis. Os JPEG en CMYK non se len (ImageIO non os admite):
 * {@code facility.photo-unreadable}.
 */
@Component
class ImageIoPhotoProcessor implements PhotoProcessor {

  static final long MAX_PIXELS = 40_000_000L;
  static final int CONCURRENT = 2;

  /** Tope do búfer decodificado: 3200 × 3200 en RGBA de 8 bits (uns 41 MB). */
  static final long DECODE_BUDGET = 3200L * 3200L * 4L;

  private static final int WORST_BYTES_PER_PIXEL = 8;
  static final String JPEG = "image/jpeg";
  private static final float QUALITY = 0.85f;
  private static final byte[] JPEG_MAGIC = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
  private static final byte[] PNG_MAGIC = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};

  private final Semaphore slots = new Semaphore(CONCURRENT, true);

  ImageIoPhotoProcessor() {
    // Sen caché en disco: os bytes xa están en memoria e /tmp é tmpfs no contedor.
    ImageIO.setUseCache(false);
  }

  @Override
  public ProcessedPhoto process(byte[] upload) {
    String format = format(upload);
    try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(upload))) {
      ImageReader reader = reader(format);
      try {
        reader.setInput(in, true, true);
        int width = reader.getWidth(0);
        int height = reader.getHeight(0);
        if ((long) width * height > MAX_PIXELS) {
          throw new DomainException(
              FacilityError.PHOTO_TOO_MANY_PIXELS, Map.of("max", MAX_PIXELS / 1_000_000));
        }
        int orientation = "jpeg".equals(format) ? ExifOrientation.read(upload) : 1;
        acquire();
        try {
          BufferedImage decoded = decode(reader, width, height);
          BufferedImage large =
              ExifOrientation.apply(fit(decoded, PhotoVariant.LARGE.maxSide()), orientation);
          BufferedImage thumb = fit(large, PhotoVariant.THUMB.maxSide());
          return new ProcessedPhoto(
              new PhotoFile(jpeg(large), JPEG),
              new PhotoFile(jpeg(thumb), JPEG),
              large.getWidth(),
              large.getHeight());
        } finally {
          slots.release();
        }
      } finally {
        reader.dispose();
      }
    } catch (IOException | RuntimeException e) {
      if (e instanceof DomainException d) {
        throw d;
      }
      throw new DomainException(FacilityError.PHOTO_UNREADABLE);
    }
  }

  /**
   * Decodifica con submostraxe para que o decodificado non pase de {@value #DECODE_BUDGET} bytes: o
   * tope de píxeles non abonda, porque unha PNG RGBA de 16 bits ocupa 8 bytes por píxel (6399 ×
   * 6250 son 320 MB e pesa 300 KB comprimida).
   */
  static BufferedImage decode(ImageReader reader, int width, int height) throws IOException {
    ImageReadParam param = reader.getDefaultReadParam();
    int step = subsampling(width, height, bytesPerPixel(reader));
    if (step > 1) {
      param.setSourceSubsampling(step, step, 0, 0);
    }
    return reader.read(0, param);
  }

  /**
   * Paso de submostraxe: o lado longo decodificado non pasa do dobre da foto grande (redondeando
   * cara arriba) e o búfer non pasa de {@value #DECODE_BUDGET} bytes.
   */
  static int subsampling(int width, int height, int bytesPerPixel) {
    int longSide = Math.max(width, height);
    int step = Math.max(1, Math.ceilDiv(longSide, 2 * PhotoVariant.LARGE.maxSide()));
    while (decodedBytes(width, height, step, bytesPerPixel) > DECODE_BUDGET) {
      step++;
    }
    return step;
  }

  static long decodedBytes(int width, int height, int step, int bytesPerPixel) {
    return (long) Math.ceilDiv(width, step) * Math.ceilDiv(height, step) * bytesPerPixel;
  }

  /** Bytes por píxel do tipo que vai decodificar o lector; se non o di, o peor caso (16 × 4). */
  static int bytesPerPixel(ImageReader reader) throws IOException {
    ImageTypeSpecifier raw = reader.getRawImageType(0);
    if (raw == null) {
      return WORST_BYTES_PER_PIXEL;
    }
    int bits = 0;
    for (int size : raw.getSampleModel().getSampleSize()) {
      bits += size;
    }
    return Math.max(1, Math.ceilDiv(bits, 8));
  }

  /** {@code jpeg} ou {@code png} polos primeiros bytes; calquera outra cousa, 415. */
  static String format(byte[] upload) {
    if (startsWith(upload, JPEG_MAGIC)) {
      return "jpeg";
    }
    if (startsWith(upload, PNG_MAGIC)) {
      return "png";
    }
    throw new DomainException(FacilityError.PHOTO_UNSUPPORTED_TYPE);
  }

  private static boolean startsWith(byte[] bytes, byte[] prefix) {
    if (bytes.length < prefix.length) {
      return false;
    }
    for (int i = 0; i < prefix.length; i++) {
      if (bytes[i] != prefix[i]) {
        return false;
      }
    }
    return true;
  }

  private static ImageReader reader(String format) {
    Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName(format);
    if (!readers.hasNext()) {
      throw new IllegalStateException("no ImageIO reader for " + format);
    }
    return readers.next();
  }

  private void acquire() {
    try {
      slots.acquire();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("interrupted while waiting to decode a photo", e);
    }
  }

  /**
   * Reduce a como moito {@code maxSide} no lado longo (nunca amplía), á metade en cada paso e
   * bilineal (mellor ca un só salto grande), en RGB sobre fondo branco.
   */
  static BufferedImage fit(BufferedImage source, int maxSide) {
    int w = source.getWidth();
    int h = source.getHeight();
    double scale = Math.min(1.0, (double) maxSide / Math.max(w, h));
    int targetW = Math.max(1, (int) Math.round(w * scale));
    int targetH = Math.max(1, (int) Math.round(h * scale));
    BufferedImage current = source;
    int cw = w;
    int ch = h;
    do {
      boolean halve = cw / 2 >= targetW && ch / 2 >= targetH;
      int nw = halve ? cw / 2 : targetW;
      int nh = halve ? ch / 2 : targetH;
      BufferedImage next = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_RGB);
      Graphics2D g = next.createGraphics();
      try {
        g.setRenderingHint(
            RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, nw, nh);
        g.drawImage(current, 0, 0, nw, nh, null);
      } finally {
        g.dispose();
      }
      current = next;
      cw = nw;
      ch = nh;
    } while (cw != targetW || ch != targetH);
    return current;
  }

  /** JPEG sen metadatos: só a cabeceira JFIF que escribe ImageIO. */
  static byte[] jpeg(BufferedImage image) throws IOException {
    Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
    if (!writers.hasNext()) {
      throw new IllegalStateException("no ImageIO JPEG writer");
    }
    ImageWriter writer = writers.next();
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try (ImageOutputStream stream = ImageIO.createImageOutputStream(out)) {
      writer.setOutput(stream);
      ImageWriteParam param = writer.getDefaultWriteParam();
      param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
      param.setCompressionQuality(QUALITY);
      writer.write(null, new IIOImage(image, null, null), param);
    } finally {
      writer.dispose();
    }
    return out.toByteArray();
  }
}
