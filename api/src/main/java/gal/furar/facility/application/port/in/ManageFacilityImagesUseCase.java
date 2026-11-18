// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.application.port.in;

/**
 * Sección Fotos da ficha (especificación, bloque 1, «Fotos»): garda nun só paso a lista de imaxes
 * da instalación (fotos da biblioteca e debuxos, na orde nova) e os textos alternativos das fotos.
 * Leva o {@code If-Match} da instalación, sobe a súa versión e devolve a ficha enteira.
 */
public interface ManageFacilityImagesUseCase {

  FacilityView arrange(ArrangeImagesCommand command);
}
