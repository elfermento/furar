// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.event;

import gal.furar.shared.tenancy.TenantAware;
import java.util.UUID;

/**
 * O concello cancelou o evento (ADR-0038). Só o escoita o propio módulo: avisa por correo a quen
 * estea apuntado.
 */
public record EventCancelled(UUID municipalityId, UUID eventId) implements TenantAware {}
