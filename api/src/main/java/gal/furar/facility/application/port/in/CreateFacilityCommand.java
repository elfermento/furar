// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.application.port.in;

import org.jspecify.annotations.Nullable;

/** Alta: o nome e, se xa o hai, o enderezo (asistente «Nova instalación»). */
public record CreateFacilityCommand(String name, @Nullable AddressInput address) {}
