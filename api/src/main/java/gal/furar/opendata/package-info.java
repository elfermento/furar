// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2

/**
 * Datos abertos (docs/glosario.md; ADR-0034): as instalacións cos seus usos, os peches e a
 * ocupación por tramos de 15 minutos de cada concello e de toda a instalación, en CSV, JSON e
 * GeoJSON, e a ficha de catálogo. Só serializa: os datos son dos seus donos ({@code facility},
 * {@code indicators}, {@code municipality}). Dúas táboas propias sen datos de quen reserva, con RLS
 * e SQL nativo. Sen API pública para outros módulos nin permisos propios: todo é público e o
 * interruptor é do concello.
 */
@ApplicationModule(
    displayName = "OpenData",
    allowedDependencies = {"shared", "facility", "indicators", "municipality"})
package gal.furar.opendata;

import org.springframework.modulith.ApplicationModule;
