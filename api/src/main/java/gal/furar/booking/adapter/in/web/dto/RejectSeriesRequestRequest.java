// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.in.web.dto;

import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/**
 * Rexeitar unha solicitude de tempada.
 *
 * @param message a mensaxe para a entidade (obrigatoria: {@code
 *     booking.series-request-message-required}); vai por correo tal cal, en texto plano
 */
public record RejectSeriesRequestRequest(@Nullable @Size(max = 1000) String message) {}
