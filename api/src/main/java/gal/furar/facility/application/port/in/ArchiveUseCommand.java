// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.application.port.in;

import gal.furar.facility.domain.FacilityId;
import gal.furar.facility.domain.UseId;

/** Arquivar («quitar») un uso coa versión do uso ({@code If-Match}). */
public record ArchiveUseCommand(FacilityId facilityId, UseId useId, long expectedVersion) {}
