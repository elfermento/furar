// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.event;

import gal.furar.shared.tenancy.TenantAware;
import java.util.UUID;

/**
 * O evento cambiou de data, hora ou lugar (ADR-0038). Só o escoita o propio módulo: avisa por
 * correo a quen estea apuntado.
 *
 * @param changeId este cambio (cada cambio avisa unha vez a cada persoa)
 */
public record EventMoved(UUID municipalityId, UUID eventId, UUID changeId) implements TenantAware {}
