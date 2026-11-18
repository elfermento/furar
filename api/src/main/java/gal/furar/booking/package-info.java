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
 * {@code BOOKING}) valídaos e gárdaos a API pública de {@code customfields} (ADR-0011). Nos usos
 * por prazas a reserva leva as súas persoas ({@code booking_participant}, ADR-0031): as fichas
 * pídeas a {@code people.PeopleQueries}, implementa a súa SPI {@code DependentUsage} e a FK a
 * {@code account_dependent} está tamén na lista pechada. Implementa {@code
 * shared.security.PersonArrival}: a reserva do mostrador pasa á conta do seu correo cando a persoa
 * entra. A lista de espera e os ocos parecidos (ADR-0033) viven aquí: {@code booking_wait} e o seu
 * reconto anónimo (as súas FK a {@code facility_use}, tamén na lista pechada), o evento {@link
 * gal.furar.booking.BookingSlotReleased} cando unha hora esperada queda libre, {@link
 * gal.furar.booking.WaitNotice} para o correo, e as alternativas por regras detrás do porto {@code
 * SlotRecommender}.
 */
@ApplicationModule(
    displayName = "Booking",
    allowedDependencies = {"shared", "facility", "municipality", "customfields", "people"})
package gal.furar.booking;

import org.springframework.modulith.ApplicationModule;
