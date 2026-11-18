// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking;

import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Vista mínima e estable dunha reserva para outros módulos (A19: só tipos da raíz e de Java).
 *
 * @param id identificador da reserva
 * @param code código {@code FUR-XXXX}
 * @param facilityId instalación
 * @param useId uso reservado
 * @param partId parte, só nos usos por partes
 * @param start inicio, incluído
 * @param end fin, excluído
 * @param status estado da reserva
 */
public record BookingSummary(
    UUID id,
    String code,
    UUID facilityId,
    UUID useId,
    @Nullable UUID partId,
    Instant start,
    Instant end,
    Status status) {

  /** Estado da reserva, como no dominio. */
  public enum Status {
    CONFIRMED,
    CANCELLED_BY_PERSON,
    CANCELLED_BY_MUNICIPALITY
  }
}
