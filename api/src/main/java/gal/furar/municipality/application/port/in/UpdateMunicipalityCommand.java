// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.municipality.application.port.in;

import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Edición do concello actual, coa versión que o cliente leu ({@code If-Match}).
 *
 * @param legal {@code null} = non cambia os datos legais
 */
public record UpdateMunicipalityCommand(
    long expectedVersion,
    String name,
    List<String> languages,
    String defaultLanguage,
    ThemeData theme,
    Map<String, Object> customFields,
    @Nullable LegalData legal) {}
