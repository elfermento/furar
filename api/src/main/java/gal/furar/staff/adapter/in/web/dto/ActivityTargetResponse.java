// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.staff.adapter.in.web.dto;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Sobre que foi a acción.
 *
 * @param kind o tipo
 * @param id o identificador (a pertenza, a reserva, a instalación, a foto ou o concello)
 * @param memberEmail o correo da pertenza, se é unha pertenza do concello
 */
public record ActivityTargetResponse(
    ActivityTargetKindValue kind, UUID id, @Nullable String memberEmail) {}
