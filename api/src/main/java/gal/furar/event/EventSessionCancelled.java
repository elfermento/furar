// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.event;

import gal.furar.shared.tenancy.TenantAware;
import java.util.UUID;

/**
 * Cancelouse unha sesión dunha actividade con horario (ADR-0041): desde a actividade ou porque a
 * súa reserva se cancelou nas reservas. Só o escoita o propio módulo: avisa por correo a quen ten
 * praza.
 */
public record EventSessionCancelled(UUID municipalityId, UUID eventId, UUID sessionId)
    implements TenantAware {}
