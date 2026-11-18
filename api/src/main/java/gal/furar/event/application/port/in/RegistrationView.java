// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.event.application.port.in;

import gal.furar.event.domain.Registration;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Unha inscrición co seu evento (o pago que se mostra depende do prezo do evento) e, na lista de
 * espera, o seu posto (1 = a primeira).
 */
public record RegistrationView(
    Registration registration, EventView event, @Nullable Integer position) {

  public RegistrationView {
    Objects.requireNonNull(registration, "registration");
    Objects.requireNonNull(event, "event");
  }

  public RegistrationView(Registration registration, EventView event) {
    this(registration, event, null);
  }
}
