// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.event;

import gal.furar.shared.tenancy.TenantAware;
import java.util.UUID;

/** Evento público do módulo: alta. Leva sempre o concello (ADR-0006). */
public record EventCreated(UUID municipalityId, UUID eventId) implements TenantAware {}
