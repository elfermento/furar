// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.adapter.in.web.dto;

import java.math.BigDecimal;
import org.jspecify.annotations.Nullable;

/**
 * Enderezo proposto polo buscador para encher o formulario. Calquera campo pode faltar (unha vía
 * sen número, un CP que non é único): o xestor complétao e a validación faise ao gardar a
 * instalación.
 */
public record AddressMatchResponse(
    @Nullable String street,
    @Nullable String streetNumber,
    @Nullable String postalCode,
    @Nullable String locality,
    @Nullable String ineCode,
    @Nullable BigDecimal latitude,
    @Nullable BigDecimal longitude) {}
