// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.adapter.in.web.dto;

import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Instalación para a veciñanza (portada e listado): só o público. Sen estado interno, borrador,
 * contacto, RIDEGA nin campos propios. {@code address}: co punto do mapa, así o listado pinta o
 * mapa sen pedir cada ficha. {@code temporarilyClosed}: vese pero hoxe non se usa (P16 do plan do
 * dominio 1). {@code covered}: cuberta (nulo = sen dato). {@code cover}: a primeira imaxe ou, se
 * non ten, o debuxo suxerido.
 */
public record PublicFacilityResponse(
    UUID id,
    String name,
    PublicLocationResponse address,
    List<SportResponse> sports,
    boolean temporarilyClosed,
    @Nullable Boolean covered,
    ImageResponse cover) {}
