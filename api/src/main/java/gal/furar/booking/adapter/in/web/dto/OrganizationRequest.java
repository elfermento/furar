// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.in.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Alta ou cambio dunha entidade (ADR-0039, punto 7).
 *
 * @param email correo da entidade, obrigatorio: os seus correos van aí
 * @param managers os correos das persoas responsables (como moito 10, sen repetir); substitúe a
 *     lista de antes
 */
public record OrganizationRequest(
    @NotBlank @Size(max = 120) String name,
    @Nullable @Size(max = 120) String contactName,
    @NotBlank @Email @Size(min = 3, max = 254) String email,
    @Nullable @Size(min = 3, max = 30) @Pattern(regexp = "^[0-9+() .-]+$") String phone,
    @NotNull List<@NotBlank @Email @Size(min = 3, max = 254) String> managers) {}
