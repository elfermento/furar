// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.adapter.in.web.portability.dto;

import gal.furar.facility.adapter.in.web.dto.SportLabelResponse;
import java.time.Instant;
import java.util.UUID;

/**
 * Deporte do concello no ficheiro furar-export (sección {@code facility-sports}), tamén se xa non o
 * usa ningunha instalación.
 */
public record CustomSportExportResponse(UUID id, SportLabelResponse label, Instant createdAt) {}
