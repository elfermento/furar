// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.incident.adapter.in.web.dto;

import java.util.UUID;

/** O aviso quedou gardado: só o seu identificador (a persoa non o volve ver). */
public record IncidentReportedResponse(UUID id) {}
