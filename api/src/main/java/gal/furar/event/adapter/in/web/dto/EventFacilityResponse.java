// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.event.adapter.in.web.dto;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** A instalación onde é o evento: nome e enderezo nunha liña. */
public record EventFacilityResponse(UUID id, String name, @Nullable String address) {}
