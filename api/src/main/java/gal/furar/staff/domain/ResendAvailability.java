// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.staff.domain;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * Se unha pertenza invitada admite outro envío segundo os seus propios límites ({@link
 * SendLimits}): a web desactiva «Reenviar» antes de que a API responda 429. O límite do concello
 * non entra: segue sendo só a resposta 429.
 *
 * @param availableAt desde cando se pode reenviar; nulo se xa se pode ou se se gastaron os envíos
 * @param exhausted se a pertenza xa gastou os seus {@value SendLimits#PER_MEMBER} envíos
 */
public record ResendAvailability(@Nullable Instant availableAt, boolean exhausted) {}
