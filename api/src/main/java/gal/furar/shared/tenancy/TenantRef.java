// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.shared.tenancy;

import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Concello da petición ou da tarefa en curso.
 *
 * @param id id do concello ({@code municipality_id})
 * @param slug identificador do URL; pode faltar nun evento asíncrono
 */
public record TenantRef(UUID id, @Nullable String slug) {

  public TenantRef {
    Objects.requireNonNull(id, "id");
  }

  public static TenantRef of(UUID id) {
    return new TenantRef(id, null);
  }
}
