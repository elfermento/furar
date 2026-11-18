// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2

/**
 * Eventos do concello (docs/glosario.md; ADR-0038): o concello publica eventos deportivos puntuais
 * (nome e descrición en gl e es, cando, nunha instalación ou noutro sitio, prazas, prezo e
 * «Apuntarse ata» opcionais, «Con aprobación») e a veciñanza apúntase ou pide praza. O evento é
 * configuración do concello (JPA, en {@code furar-export}); as inscricións, datos da persoa en SQL
 * nativo con RLS e fóra de {@code furar-export}, coma as reservas. Depende da API pública de {@code
 * facility} (o lugar e a súa portada), de {@code notification} (os correos, {@code EventMailer},
 * despois do commit) e, desde ADR-0041, de {@code booking} (as sesións dunha actividade ocupan a
 * instalación con {@code ActivityBookings}) e de {@code people} (as persoas a cargo). Ningún deles
 * depende de {@code event}. API pública: {@link gal.furar.event.EventQueries}, {@link
 * gal.furar.event.EventSummary} e {@link gal.furar.event.EventCreated}; os demais eventos só os
 * escoita o propio módulo.
 */
@ApplicationModule(
    displayName = "Event",
    allowedDependencies = {"shared", "facility", "notification", "booking", "people"})
package gal.furar.event;

import org.springframework.modulith.ApplicationModule;
