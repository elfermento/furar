// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Un correo «Lista de espera cancelada» pendente (ADR-0033, revisión do 2026-11-11; A19: só tipos
 * da raíz e de Java): a quen escribir, en que idioma, o nome de agora da instalación e as franxas
 * polas que esperaba, en orde. Unha persoa con varias esperas na mesma instalación recibe un só
 * correo con todas.
 *
 * @param noticeId a primeira das súas esperas canceladas: o destino do rexistro de envíos
 * @param email a clave do correo ({@code EmailKey.of})
 * @param language {@code gl} ou {@code es}
 * @param slots as franxas, por inicio
 */
public record CancelledWaitsNotice(
    UUID noticeId,
    String email,
    String language,
    UUID facilityId,
    String facilityName,
    List<Slot> slots) {

  public CancelledWaitsNotice {
    Objects.requireNonNull(noticeId, "noticeId");
    Objects.requireNonNull(email, "email");
    Objects.requireNonNull(language, "language");
    Objects.requireNonNull(facilityId, "facilityId");
    Objects.requireNonNull(facilityName, "facilityName");
    slots = List.copyOf(slots);
    if (slots.isEmpty()) {
      throw new IllegalArgumentException("No slots");
    }
  }

  /** Unha franxa pola que esperaba. */
  public record Slot(Instant start, Instant end) {

    public Slot {
      Objects.requireNonNull(start, "start");
      Objects.requireNonNull(end, "end");
    }
  }
}
