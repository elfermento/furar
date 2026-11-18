// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.application.port.in;

import gal.furar.facility.domain.FacilityId;

/**
 * Pago e devolución da instalación en gl e es (os dous textos dunha nota baleiros = sen nota);
 * {@code expectedVersion} sae do {@code If-Match}.
 */
public record UpdateFacilityPaymentNotesCommand(
    FacilityId id, long expectedVersion, DescriptionInput payment, DescriptionInput refund) {}
