// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.event;

import gal.furar.shared.tenancy.TenantAware;
import java.time.LocalDate;
import java.util.UUID;

/**
 * O concello rematou antes unha actividade con horario (ADR-0041). Só o escoita o propio módulo:
 * avisa por correo a quen ten praza.
 *
 * @param changeId este cambio (cada un avisa unha vez a cada persoa)
 * @param from desde que día xa non hai sesións
 */
public record EventEndedEarly(UUID municipalityId, UUID eventId, UUID changeId, LocalDate from)
    implements TenantAware {}
