// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.event.application.port.in;

import gal.furar.event.domain.EventSession;
import java.util.Objects;

/** Unha sesión con cantas persoas estaban (presentes) e cantas faltaron, só números. */
public record SessionView(EventSession session, int present, int absent) {

  public SessionView {
    Objects.requireNonNull(session, "session");
  }
}
