// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.adapter.in.web.dto;

import java.math.BigDecimal;
import org.jspecify.annotations.Nullable;

/** Enderezo gardado: vía, número e coordenadas (poden faltar), CP, localidade e INE. */
public record AddressResponse(
    String street,
    @Nullable String streetNumber,
    String postalCode,
    String locality,
    String ineCode,
    @Nullable BigDecimal latitude,
    @Nullable BigDecimal longitude) {}
