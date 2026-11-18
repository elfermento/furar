// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.domain;

import gal.furar.shared.domain.Language;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Quen se une a unha partida aberta ou o pide (ADR-0040): sen reserva propia, sen QR e sen contar
 * no seu límite nin nas súas faltas. A conta, o nome, o correo e o idioma veñen do token; o nome só
 * o ven as persoas da partida. {@link #toString()} non mostra nin o nome nin o correo.
 *
 * @param decidedAt cando entrou ou cando quen organiza decidiu; nulo mentres está pendente
 */
public record OpenMatchPlayer(
    UUID id,
    UUID matchId,
    String accountSubject,
    String email,
    String name,
    Language language,
    OpenMatchPlayerStatus status,
    Instant createdAt,
    @Nullable Instant decidedAt) {

  public OpenMatchPlayer {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(matchId, "matchId");
    Objects.requireNonNull(accountSubject, "accountSubject");
    Objects.requireNonNull(email, "email");
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(language, "language");
    Objects.requireNonNull(status, "status");
    Objects.requireNonNull(createdAt, "createdAt");
    if (status == OpenMatchPlayerStatus.PENDING && decidedAt != null) {
      throw new IllegalStateException("A pending request is not decided");
    }
    if (status == OpenMatchPlayerStatus.REJECTED && decidedAt == null) {
      throw new IllegalStateException("A rejected request has its decision");
    }
  }

  /** Quen entra: dentro sen aprobación; pendente con ela. */
  public static OpenMatchPlayer join(
      UUID id,
      UUID matchId,
      String subject,
      String email,
      String name,
      Language language,
      boolean approval,
      Instant now) {
    return new OpenMatchPlayer(
        id,
        matchId,
        subject,
        email,
        OpenMatch.publicName(name),
        language,
        approval ? OpenMatchPlayerStatus.PENDING : OpenMatchPlayerStatus.CONFIRMED,
        now,
        approval ? null : now);
  }

  @Override
  public String toString() {
    return "OpenMatchPlayer[id=" + id + ", matchId=" + matchId + ", status=" + status + "]";
  }
}
