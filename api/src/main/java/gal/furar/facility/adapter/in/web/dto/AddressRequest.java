// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.adapter.in.web.dto;

import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import org.jspecify.annotations.Nullable;

/**
 * Enderezo da alta. Só limita o tamaño: que falta ou que non vale dío o dominio ({@code
 * facility.address-required}, {@code facility.address-invalid}, con {@code errors[]} por campo).
 */
public record AddressRequest(
    @Nullable @Size(max = 200) String street,
    @Nullable @Size(max = 20) String streetNumber,
    @Nullable @Size(max = 5) String postalCode,
    @Nullable @Size(max = 100) String locality,
    @Nullable @Size(max = 5) String ineCode,
    @Nullable BigDecimal latitude,
    @Nullable BigDecimal longitude) {}
