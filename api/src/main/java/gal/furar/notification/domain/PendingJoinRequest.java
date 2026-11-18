// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.notification.domain;

import gal.furar.shared.domain.Language;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Unha solicitude de alta cuxo correo non saíu á primeira (ADR-0042, revisión do 2026-11-17):
 * gárdase para reintentala e bórrase ao enviarse ou ao pasar o prazo de conservación.
 *
 * <p>{@link #toString()} non leva datos persoais: só o concello (o de {@link JoinRequest}).
 *
 * @param language o idioma no que se encheu o formulario, o do correo
 * @param receivedAt cando chegou
 * @param attempts envíos intentados, contando o primeiro
 */
public record PendingJoinRequest(
    UUID id, JoinRequest request, Language language, Instant receivedAt, int attempts) {

  public PendingJoinRequest {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(request, "request");
    Objects.requireNonNull(language, "language");
    Objects.requireNonNull(receivedAt, "receivedAt");
  }
}
