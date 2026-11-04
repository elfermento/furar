// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
/**
 * Instalacións (docs/glosario.md; ADR-0022): instalación, estado e borrador calculado; despois usos
 * (con días da semana, prezo e normas), fotos, deportes, enderezos, horario e bloqueos. API
 * pública: {@link gal.furar.facility.FacilityQueries}, {@link gal.furar.facility.FacilitySummary},
 * {@link gal.furar.facility.UseSummary}, {@link gal.furar.facility.UseTerms}, {@link
 * gal.furar.facility.FacilityPaymentTexts}, {@link gal.furar.facility.OpeningInterval}, {@link
 * gal.furar.facility.FacilityCreated} e a SPI {@link gal.furar.facility.BookingImpact} cos seus
 * records. Os campos propios da instalación (destino {@code FACILITY}) gárdaos {@code customfields}
 * pola súa API pública.
 */
@ApplicationModule(
    displayName = "Facility",
    allowedDependencies = {"shared", "customfields"})
package gal.furar.facility;

import org.springframework.modulith.ApplicationModule;
