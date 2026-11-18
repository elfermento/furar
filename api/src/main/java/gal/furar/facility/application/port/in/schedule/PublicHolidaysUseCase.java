// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.application.port.in.schedule;

import gal.furar.facility.domain.schedule.PublicHoliday;
import java.util.List;

/** Festivos de Galicia dun ano (sen sesión: son datos públicos da plataforma). */
public interface PublicHolidaysUseCase {

  List<PublicHoliday> holidays(int year);
}
