// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.staff.domain;

import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Objects;

/**
 * SHA-256 dun token de invitación: o único que se garda ({@code staff_invitation.token_hash}). Por
 * el búscase a invitación; o token en claro non sae do correo nin da memoria do caso de uso.
 */
public final class TokenHash {

  public static final int LENGTH = 32;

  private final byte[] bytes;

  private TokenHash(byte[] bytes) {
    this.bytes = bytes;
  }

  /** Desde a base. */
  public static TokenHash of(byte[] bytes) {
    Objects.requireNonNull(bytes, "bytes");
    if (bytes.length != LENGTH) {
      throw new IllegalArgumentException("A token hash has " + LENGTH + " bytes");
    }
    return new TokenHash(bytes.clone());
  }

  public byte[] bytes() {
    return bytes.clone();
  }

  @Override
  public boolean equals(Object other) {
    // Tempo constante: a comparación non di canto se parecen dous hash.
    return other instanceof TokenHash that && MessageDigest.isEqual(bytes, that.bytes);
  }

  @Override
  public int hashCode() {
    return Arrays.hashCode(bytes);
  }

  /** Só os primeiros bytes: abonda para depurar e non serve para buscar a invitación. */
  @Override
  public String toString() {
    return "TokenHash[" + HexFormat.of().formatHex(bytes, 0, 4) + "…]";
  }
}
