// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.application.port.in;

import java.math.BigDecimal;
import org.jspecify.annotations.Nullable;

/** Enderezo tal como chega; o dominio ({@code Address}) dí que falta ou que non vale. */
public record AddressInput(
    @Nullable String street,
    @Nullable String streetNumber,
    @Nullable String postalCode,
    @Nullable String locality,
    @Nullable String ineCode,
    @Nullable BigDecimal latitude,
    @Nullable BigDecimal longitude) {}
