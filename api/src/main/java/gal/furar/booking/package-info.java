// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
/**
 * Reservas (docs/glosario.md; ADR-0012, ADR-0025): a reserva dun oco dun uso, a ocupación na base
 * (un uso por instalación e franxa, o cupo e unha praza por persoa), o cobro no mostrador e a súa
 * historia. Depende da API pública de {@code facility} (usos, horario e o bloqueo da instalación) e
 * de {@code municipality}; as súas FK a {@code facility_use} e {@code facility_use_part} están na
 * lista pechada {@code CrossModuleForeignKeys}. API pública: {@link
 * gal.furar.booking.BookingQueries}, {@link gal.furar.booking.BookingSummary} e os agregados dos
 * indicadores, {@link gal.furar.booking.BookingStatistics}. Os campos propios da reserva (destino
 * {@code BOOKING}) valídaos e gárdaos a API pública de {@code customfields} (ADR-0011).
 */
@ApplicationModule(
    displayName = "Booking",
    allowedDependencies = {"shared", "facility", "municipality", "customfields"})
package gal.furar.booking;

import org.springframework.modulith.ApplicationModule;
