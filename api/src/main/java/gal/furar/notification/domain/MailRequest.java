// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.notification.domain;

import gal.furar.shared.domain.Language;
import java.util.Objects;
import java.util.UUID;

/** Que correo enviar, a quen e en que idioma: a clave do rexistro de envíos. */
public record MailRequest(
    UUID bookingId, MailKind kind, String eventId, String recipient, Language language) {

  public MailRequest {
    Objects.requireNonNull(bookingId, "bookingId");
    Objects.requireNonNull(kind, "kind");
    Objects.requireNonNull(eventId, "eventId");
    Objects.requireNonNull(recipient, "recipient");
    Objects.requireNonNull(language, "language");
  }
}
