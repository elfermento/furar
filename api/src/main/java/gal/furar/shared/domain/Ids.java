// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.shared.domain;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.UUID;

/**
 * Xerador de UUIDv7 (RFC 9562): 48 bits de milisegundos Unix, versión 7, 74 bits aleatorios. Ordena
 * por tempo de creación, o que favorece os índices B-tree (ADR-0010).
 */
public final class Ids {

  private static final SecureRandom RANDOM = new SecureRandom();

  private Ids() {}

  /** Novo UUIDv7 co reloxo do sistema. */
  public static UUID newV7() {
    return newV7(Clock.systemUTC());
  }

  /** Novo UUIDv7 co reloxo indicado (para tests deterministas da parte temporal). */
  public static UUID newV7(Clock clock) {
    long millis = clock.millis();
    long randA = RANDOM.nextInt(1 << 12);
    long msb = (millis & 0xFFFF_FFFF_FFFFL) << 16 | 0x7000L | randA;
    long lsb = RANDOM.nextLong() & 0x3FFF_FFFF_FFFF_FFFFL | 0x8000_0000_0000_0000L;
    return new UUID(msb, lsb);
  }
}
