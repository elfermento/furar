// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.in.web.dto;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Unha fila da táboa de entidades (ADR-0039, punto 7).
 *
 * @param managers cantos responsables ten
 */
public record OrganizationRowResponse(
    UUID id,
    String name,
    @Nullable String contactName,
    @Nullable String email,
    @Nullable String phone,
    int managers,
    OrganizationStatusValue status,
    boolean archived) {}
