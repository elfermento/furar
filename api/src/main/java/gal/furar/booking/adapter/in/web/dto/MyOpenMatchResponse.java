// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.in.web.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Unha partida aberta para a persoa con sesión (ADR-0040, punto 9): o que se ve en público (en
 * calquera estado para quen está dentro), o seu papel e, se está dentro, os nomes.
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
 * @param description texto plano
 * @param deadline o prazo do concello
 * @param me o seu papel
 * @param hidden se o concello a ocultou («O concello ocultou esta partida»)
 * @param knownCount «Xa somos», con quen organiza («+ N» se é máis dun)
 * @param bookingId a reserva, só para quen organiza (editar e «Deixar de buscar»)
 * @param players as persoas que están dentro, só para quen está dentro; baleira para o resto
 * @param requests as solicitudes pendentes, só para quen organiza
 * @param version a do {@code ETag}, para o {@code If-Match} de editar
 */
public record MyOpenMatchResponse(
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
    boolean approval,
    @Nullable String description,
    Instant deadline,
    OpenMatchStatusValue status,
    OpenMatchMeResponse me,
    boolean hidden,
    int knownCount,
    @Nullable UUID bookingId,
    List<OpenMatchPersonResponse> players,
    List<OpenMatchJoinRequestResponse> requests,
    long version) {}
