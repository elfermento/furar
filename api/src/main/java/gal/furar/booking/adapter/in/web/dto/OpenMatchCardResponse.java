// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.in.web.dto;

import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Unha partida aberta nunha lista (ADR-0040): portada do concello, ficha da instalación e portada
 * común. Só as que teñen sitio.
 *
 * @param facilityAddress enderezo da instalación nunha liña
 * @param cover portada da instalación
 * @param partName a parte, só nos usos por partes
 * @param start inicio da reserva
 * @param end fin da reserva
 * @param level nulo = «Sen indicar»
 * @param title nulo = sen título (a web pon «uso · día hora»)
 * @param organizerName o nome completo de quen organiza: o único nome público
 * @param total as persoas que van: «Xa somos» e as que se uniron («4 de 10»)
 * @param minimum para que se xogue
 * @param maximum persoas totais
 * @param approval «Con aprobación»: quen quere entrar pídeo
 */
public record OpenMatchCardResponse(
    UUID id,
    UUID facilityId,
    String facilityName,
    @Nullable String facilityAddress,
    @Nullable CoverImageResponse cover,
    String useName,
    @Nullable String partName,
    Instant start,
    Instant end,
    @Nullable OpenMatchLevelValue level,
    @Nullable String title,
    String organizerName,
    int total,
    int minimum,
    int maximum,
    boolean approval) {}
