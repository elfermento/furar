// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.application.port.in.schedule;

import gal.furar.facility.domain.FacilityId;
import gal.furar.facility.domain.schedule.SeasonId;
import gal.furar.facility.domain.schedule.SeasonSpec;
import org.jspecify.annotations.Nullable;

/** «+ Tempada»: nome, desde, ata e «empezar co horario de» ({@code copyFrom}, nulo = baleira). */
public record CreateSeasonCommand(
    FacilityId facilityId, SeasonSpec spec, @Nullable SeasonId copyFrom) {}
