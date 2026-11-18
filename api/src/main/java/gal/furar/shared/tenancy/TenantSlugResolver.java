// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.shared.tenancy;

import java.util.Optional;

/**
 * Porto para resolver o slug do URL ao id do concello sen abrir unha sesión JPA. Implementao o
 * módulo {@code municipality}.
 */
public interface TenantSlugResolver {

  Optional<TenantRef> resolve(String slug);
}
