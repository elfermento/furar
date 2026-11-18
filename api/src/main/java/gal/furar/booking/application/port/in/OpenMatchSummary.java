// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.application.port.in;

import gal.furar.booking.domain.OpenMatchStatus;
import java.util.Objects;
import java.util.UUID;

/**
 * A partida aberta dunha reserva, nos números (ADR-0040, punto 10): para a tarxeta e o detalle de
 * quen reservou e para o detalle do persoal. Sen nomes.
 *
 * @param total as persoas que van: «Xa somos» e as que se uniron
 * @param confirmed as persoas que se uniron e están dentro (as que se avisan se se cancela)
 * @param pending as solicitudes pendentes
 */
public record OpenMatchSummary(
    UUID id,
    OpenMatchStatus status,
    boolean hidden,
    int total,
    int minimum,
    int maximum,
    int confirmed,
    int pending) {

  public OpenMatchSummary {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(status, "status");
  }
}
