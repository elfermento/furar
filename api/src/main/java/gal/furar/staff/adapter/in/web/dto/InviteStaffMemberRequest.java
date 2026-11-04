// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.staff.adapter.in.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * Invitar a unha persoa ao persoal do concello.
 *
 * @param email o seu correo; se non ten forma de correo, 422 {@code staff.email-invalid}
 * @param role o rol
 * @param language o idioma do correo; sen el, o da petición ({@code Accept-Language})
 */
public record InviteStaffMemberRequest(
    @NotNull @Schema(example = "persoa@concello.gal") String email,
    @NotNull StaffRoleValue role,
    @Nullable @Pattern(regexp = "^(gl|es)$") @Schema(allowableValues = {"gl", "es"})
        String language) {}
