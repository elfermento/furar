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
 * sanéanse outra vez ao importar: o ficheiro pode vir de calquera sitio. Un ficheiro anterior a
 * unha sección opcional trae ese campo a {@code null}: a penalización das faltas e «Buscar xente
 * para xogar» quedan apagadas e os datos abertos e o aviso para instalar no móbil, acesos
 * (ADR-0034, punto 22).
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
    @Nullable LegalTextsData legalTexts,
    @Nullable NoShowPolicyData noShowPolicy,
    @Nullable Boolean openData,
    @Nullable OpenMatchPolicyData openMatchPolicy,
    @Nullable Boolean installHint) {

  /**
   * A penalización das faltas do ficheiro (ADR-0032). O momento en que se acendeu non viaxa: se vén
   * acesa, conta desde a importación.
   */
  public record NoShowPolicyData(boolean enabled, int threshold, int windowDays, int blockDays) {}

  /**
   * «Buscar xente para xogar» do ficheiro (ADR-0040). O momento en que se acendeu non viaxa: se vén
   * aceso, conta desde a importación.
   */
  public record OpenMatchPolicyData(boolean enabled, int cancelHours) {}
}
