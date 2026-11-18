// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.in.web.dto;

import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Unha entidade (ADR-0039, punto 7). Arquivada, só o nome.
 *
 * @param managers os correos das persoas responsables
 */
public record OrganizationResponse(
    UUID id,
    String name,
    @Nullable String contactName,
    @Nullable String email,
    @Nullable String phone,
    List<String> managers,
    boolean archived,
    long version) {}
