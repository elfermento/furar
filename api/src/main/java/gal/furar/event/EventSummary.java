// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Vista mínima e estable dun evento para outros módulos (A19: só tipos da raíz e de Java).
 *
 * @param name o nome en galego
 */
public record EventSummary(UUID id, String name, Instant startsAt) {}
