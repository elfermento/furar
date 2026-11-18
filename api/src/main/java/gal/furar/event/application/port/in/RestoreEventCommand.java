// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.event.application.port.in;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Fila da sección event dun ficheiro furar-export: o evento co seu id e as súas datas, sen as
 * persoas apuntadas (datos persoais, fóra do ficheiro coma as reservas) nin as sesións dunha
 * actividade (as súas reservas tampouco van no ficheiro, ADR-0041).
 *
 * @param endsAt o fin dunha actividade (nulo nun evento dun día ou nunha versión anterior)
 * @param endedFrom desde que día rematou antes unha actividade
 */
public record RestoreEventCommand(
    UUID id,
    EventCommand details,
    @Nullable Instant endsAt,
    @Nullable LocalDate endedFrom,
    @Nullable Instant cancelledAt,
    Instant createdAt,
    Instant updatedAt) {}
