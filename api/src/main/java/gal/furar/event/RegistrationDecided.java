// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.event;

import gal.furar.shared.tenancy.TenantAware;
import java.util.UUID;

/**
 * O concello aceptou ou rexeitou unha solicitude dun evento «Con aprobación» (ADR-0038). Só o
 * escoita o propio módulo: avisa á persoa por correo.
 */
public record RegistrationDecided(UUID municipalityId, UUID registrationId)
    implements TenantAware {}
