// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.application.port.in.schedule;

import gal.furar.facility.domain.FacilityId;
import gal.furar.facility.domain.schedule.BlockId;

/**
 * Borrar un bloqueo coa súa versión ({@code If-Match}). Sen modal: quitar un bloqueo non deixa fóra
 * ningunha reserva.
 */
public record DeleteBlockCommand(FacilityId facilityId, BlockId blockId, long expectedVersion) {}
