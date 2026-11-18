// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.application.port.in;

/**
 * «Publicar» do asistente «Nova instalación» baixo o bloqueo da instalación: sen enderezo,
 * deportes, usos ou horario, 409 {@code facility.not-publishable}. Publicar unha publicada non
 * cambia nada.
 */
public interface PublishFacilityUseCase {

  FacilityView publish(PublishFacilityCommand command);
}
