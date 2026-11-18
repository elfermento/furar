// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.event.adapter.in.web.dto;

/**
 * A imaxe do evento: a portada da súa instalación ou, noutro sitio, o debuxo xenérico. {@code
 * large} e {@code thumb} son rutas públicas; {@code alt} baleiro se só decora.
 */
public record EventCoverResponse(String large, String thumb, EventTextResponse alt) {}
