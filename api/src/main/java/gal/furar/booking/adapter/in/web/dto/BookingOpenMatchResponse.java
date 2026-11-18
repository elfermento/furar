// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.in.web.dto;

import java.util.UUID;

/**
 * A partida aberta dunha reserva, nos números e sen nomes (ADR-0040): na tarxeta e no detalle de
 * quen reservou, e no detalle do persoal.
 *
 * @param hidden se o concello a ocultou
 * @param total as persoas que van: «Xa somos» e as que se uniron
 * @param confirmed as persoas que se uniron e están dentro («avisamos ás N persoas apuntadas»)
 * @param pending as solicitudes pendentes
 */
public record BookingOpenMatchResponse(
    UUID id,
    OpenMatchStatusValue status,
    boolean hidden,
    int total,
    int minimum,
    int maximum,
    int confirmed,
    int pending) {}
