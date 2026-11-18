// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.municipality;

import gal.furar.shared.tenancy.TenantAware;
import java.util.UUID;

/** Evento público: alta dun concello. */
public record MunicipalityCreated(UUID municipalityId, String slug) implements TenantAware {}
