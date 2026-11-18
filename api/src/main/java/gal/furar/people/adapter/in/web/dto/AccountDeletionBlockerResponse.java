// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.people.adapter.in.web.dto;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * O que impide eliminar a conta (ADR-0043), para ligalo: as reservas e as partidas, a «As miñas
 * reservas» do concello; o evento, á súa ficha.
 *
 * @param kind que é
 * @param municipality o slug do concello; nulo na administración da plataforma
 * @param municipalityName o nome do concello
 * @param eventId o evento ou a actividade (só {@code EVENT})
 * @param eventName o seu nome no idioma da petición (só {@code EVENT})
 * @param count cantas
 */
public record AccountDeletionBlockerResponse(
    Kind kind,
    @Nullable String municipality,
    @Nullable String municipalityName,
    @Nullable UUID eventId,
    @Nullable String eventName,
    long count) {

  /** Os tipos (o mesmo ca o dominio, para o contrato). */
  public enum Kind {
    BOOKING,
    OPEN_MATCH,
    EVENT,
    STAFF,
    PLATFORM_ADMIN
  }
}
