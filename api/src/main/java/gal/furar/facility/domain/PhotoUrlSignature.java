// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.domain;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.jspecify.annotations.Nullable;

/**
 * Firma das rutas dos bytes das fotos que a API dá nas respostas da xestión e das reservas
 * (ADR-0024, «Bytes públicos»). Un {@code <img>} non leva a sesión, así que a ruta leva a proba de
 * que a deu a API: {@code …/photos/{id}/large?s=…}. Sen firma válida só se serven as fotos das
 * instalacións que se ven no público; con ela, tamén as dos borradores, as sen publicar e as
 * pendentes.
 *
 * <p>A chave é aleatoria en cada arranque da API (sen configuración): despois dun reinicio as rutas
 * vellas dos borradores deixan de valer ata que a web volve pedir os datos; as fotos públicas
 * séguense vendo sen firma. Cada firma vale na semana en que se dá e na seguinte, así que unha ruta
 * que se viu nunha instalación que despois se retira caduca nunhas dúas semanas.
 */
public final class PhotoUrlSignature {

  /** Parámetro da ruta coa firma. */
  public static final String PARAM = "s";

  static final Duration PERIOD = Duration.ofDays(7);

  private static final String ALGORITHM = "HmacSHA256";
  private static final int LENGTH = 16;
  private static final byte[] KEY = randomKey();
  private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

  private PhotoUrlSignature() {}

  /** A firma desta foto deste concello para o período de {@code now}. */
  public static String sign(UUID municipalityId, UUID photoId, Instant now) {
    return ENCODER.encodeToString(mac(municipalityId, photoId, period(now)));
  }

  /** Se a firma é desta foto e deste concello, dada neste período ou no anterior. */
  public static boolean verify(
      UUID municipalityId, UUID photoId, @Nullable String signature, Instant now) {
    if (signature == null || signature.isBlank()) {
      return false;
    }
    byte[] given;
    try {
      given = Base64.getUrlDecoder().decode(signature);
    } catch (IllegalArgumentException e) {
      return false;
    }
    long current = period(now);
    return MessageDigest.isEqual(given, mac(municipalityId, photoId, current))
        || MessageDigest.isEqual(given, mac(municipalityId, photoId, current - 1));
  }

  private static long period(Instant now) {
    return Math.floorDiv(now.getEpochSecond(), PERIOD.toSeconds());
  }

  private static byte[] mac(UUID municipalityId, UUID photoId, long period) {
    try {
      Mac mac = Mac.getInstance(ALGORITHM);
      mac.init(new SecretKeySpec(KEY, ALGORITHM));
      mac.update("furar-photo".getBytes(StandardCharsets.US_ASCII));
      mac.update(
          ByteBuffer.allocate(40)
              .putLong(municipalityId.getMostSignificantBits())
              .putLong(municipalityId.getLeastSignificantBits())
              .putLong(photoId.getMostSignificantBits())
              .putLong(photoId.getLeastSignificantBits())
              .putLong(period)
              .array());
      return Arrays.copyOf(mac.doFinal(), LENGTH);
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("HmacSHA256 unavailable", e);
    }
  }

  private static byte[] randomKey() {
    byte[] key = new byte[32];
    new SecureRandom().nextBytes(key);
    return key;
  }
}
