// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.application.port.in;

import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Os datos dunha entidade tal como os escribe o xestor (ADR-0039, punto 7).
 *
 * @param managers os correos das persoas responsables; a lista substitúe a de antes
 */
public record OrganizationCommand(
    String name,
    @Nullable String contactName,
    String email,
    @Nullable String phone,
    List<String> managers) {

  public OrganizationCommand {
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(email, "email");
    managers = managers == null ? List.of() : List.copyOf(managers);
  }
}
