// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.@module@.application.port.in;

import java.util.Map; // @cf

/** Alta. {@code customFields}: valores tal como chegan; valídaos o caso de uso (ADR-0011). */
public record Create@Name@Command(
    Map<String, Object> customFields, // @cf
    String name) {}
