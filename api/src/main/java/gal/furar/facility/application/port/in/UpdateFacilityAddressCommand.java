// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.application.port.in;

import gal.furar.facility.domain.FacilityId;
import org.jspecify.annotations.Nullable;

/** Sección Localización: {@code expectedVersion} sae do {@code If-Match}. */
public record UpdateFacilityAddressCommand(
    FacilityId id, long expectedVersion, @Nullable AddressInput address) {}
