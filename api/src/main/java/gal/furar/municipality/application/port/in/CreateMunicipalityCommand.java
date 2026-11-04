// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.municipality.application.port.in;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Alta dun concello.
 *
 * @param theme {@code null} = tema por defecto
 * @param legal {@code null} = sen datos legais
 */
public record CreateMunicipalityCommand(
    String slug,
    String name,
    List<String> languages,
    String defaultLanguage,
    @Nullable ThemeData theme,
    @Nullable LegalData legal) {}
