// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.municipality.application.port.in;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Concello dun ficheiro {@code furar-export}: os seus datos e as filas de cada sección, xa co tipo
 * da sección ({@code MunicipalityDataSection#rowType}). Os textos propios das páxinas legais
 * sanéanse outra vez ao importar: o ficheiro pode vir de calquera sitio.
 */
public record ImportMunicipalityCommand(
    UUID id,
    String slug,
    String name,
    List<String> languages,
    String defaultLanguage,
    ThemeData theme,
    @Nullable Map<String, Object> customFields,
    Instant createdAt,
    Instant updatedAt,
    Map<String, List<?>> sections,
    @Nullable LegalData legal,
    @Nullable LegalTextsData legalTexts) {}
