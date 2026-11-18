// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.incident.application.port.in;

/**
 * O persoal cambia o estado, a nota interna e, ao resolver, a mensaxe para a persoa (ADR-0036).
 * Pasar a Resolta un aviso da veciñanza envíalle un correo despois do commit.
 */
public interface ReviewIncidentUseCase {

  IncidentView review(ReviewIncidentCommand command);
}
