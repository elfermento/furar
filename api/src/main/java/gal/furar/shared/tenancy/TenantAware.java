// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.shared.tenancy;

import java.util.UUID;

/**
 * Evento ou mensaxe que pertence a un concello. Os escoitadores abren o seu concello antes da
 * transacción (ADR-0006).
 */
public interface TenantAware {

  UUID municipalityId();
}
