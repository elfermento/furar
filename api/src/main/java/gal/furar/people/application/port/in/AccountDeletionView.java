// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.people.application.port.in;

import gal.furar.people.domain.AccountDeletionBlocker;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Un impedimento para eliminar a conta, listo para mostrar e ligar (ADR-0043).
 *
 * @param kind que é
 * @param slug o concello no URL; nulo na administración da plataforma
 * @param municipalityName o nome do concello; nulo na administración da plataforma
 * @param eventId o evento ou a actividade ({@link AccountDeletionBlocker.Kind#EVENT})
 * @param eventNameGl o seu nome en galego
 * @param eventNameEs o seu nome en castelán
 * @param count cantas
 */
public record AccountDeletionView(
    AccountDeletionBlocker.Kind kind,
    @Nullable String slug,
    @Nullable String municipalityName,
    @Nullable UUID eventId,
    @Nullable String eventNameGl,
    @Nullable String eventNameEs,
    long count) {

  public AccountDeletionView {
    Objects.requireNonNull(kind, "kind");
  }
}
