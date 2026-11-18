// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
/**
 * Incidencias (docs/glosario.md; ADR-0036): a veciñanza avisa dun problema nunha instalación (tipo,
 * texto e ata tres fotos), desde a ficha pública ou desde unha reserva do seu historial, e o
 * persoal do concello (Xestión e Recepción) cambia o estado (Nova, En curso, Resolta), escribe unha
 * nota interna e, ao resolver, unha mensaxe para a persoa; tamén pode anotar unha el mesmo. Non son
 * públicas nin as ve a plataforma. SQL nativo con RLS, sen JPA, e fóra de {@code furar-export},
 * coma as reservas (leva datos da persoa). Depende da API pública de {@code facility} (a
 * instalación e o tratamento das fotos, {@code PhotoSanitizer}), de {@code booking} (a reserva da
 * que vén o aviso), de {@code staff} (a quen avisar da xestión) e de {@code notification} (os
 * correos, {@code IncidentMailer}, despois do commit). Sen API pública propia: os eventos {@link
 * gal.furar.incident.IncidentReported} e {@link gal.furar.incident.IncidentResolved} só os escoita
 * o propio módulo.
 */
@ApplicationModule(
    displayName = "Incident",
    allowedDependencies = {"shared", "facility", "booking", "staff", "notification"})
package gal.furar.incident;

import org.springframework.modulith.ApplicationModule;
