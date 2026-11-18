// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.event;

import gal.furar.shared.tenancy.TenantAware;
import java.util.UUID;

/**
 * Avisouse a unha persoa da lista de espera dun evento de que hai unha praza libre (ADR-0041). Só o
 * escoita o propio módulo: mándalle o correo se o aviso xa corre (de noite, sae ás 8:00 coa tarefa
 * da lista de espera).
 */
public record EventWaitlistNotified(UUID municipalityId, UUID registrationId)
    implements TenantAware {}
