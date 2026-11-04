// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.municipality.application.port.in;

import org.jspecify.annotations.Nullable;

/**
 * As dúas notas en gl e es; os dous textos dunha nota baleiros = o texto por defecto de Furar.
 * {@code scope}: {@code ALL} ou {@code FACILITY}; nulo = o que xa había.
 */
public record UpdatePaymentNotesCommand(
    long expectedVersion,
    @Nullable String scope,
    @Nullable String paymentGl,
    @Nullable String paymentEs,
    @Nullable String refundGl,
    @Nullable String refundEs) {}
