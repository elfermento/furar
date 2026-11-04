// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.adapter.in.web.dto;

import java.math.BigDecimal;
import org.jspecify.annotations.Nullable;

/** Enderezo da ficha pública co punto do mapa, se o ten (sen o código INE, que é interno). */
public record PublicLocationResponse(
    String street,
    @Nullable String streetNumber,
    String postalCode,
    String locality,
    @Nullable BigDecimal latitude,
    @Nullable BigDecimal longitude) {}
