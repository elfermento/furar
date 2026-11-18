// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.adapter.in.web.portability.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Parte arquivada dun uso no ficheiro furar-export: xa non sae no uso, pero as reservas poden
 * apuntar a ela. {@code position} é a que tiña ao quitala.
 */
public record ArchivedPartExportResponse(
    UUID id, UUID useId, String name, int position, Instant archivedAt) {}
