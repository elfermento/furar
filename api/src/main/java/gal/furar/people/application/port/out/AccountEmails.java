// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.people.application.port.out;

import java.time.Instant;
import java.util.Optional;

/** As contas coñecidas polo seu correo verificado ({@code account_email}; ADR-0031, punto 4). */
public interface AccountEmails {

  /** O que hai gardado da conta. */
  Optional<Seen> find(String subject);

  /**
   * Garda o correo da conta: quita ese correo de calquera outra conta e escribe a fila se é nova ou
   * se {@code seenAt} é posterior ao gardado.
   */
  void write(String subject, String emailKey, Instant seenAt);

  /** A conta que entrou con ese correo. */
  Optional<String> subjectOf(String emailKey);

  /** Borra as contas vistas por última vez antes de {@code before} e di cantas. */
  int purgeSeenBefore(Instant before);

  /**
   * @param emailKey o correo normalizado
   * @param seenAt o {@code auth_time} da última sesión gardada
   */
  record Seen(String emailKey, Instant seenAt) {}
}
