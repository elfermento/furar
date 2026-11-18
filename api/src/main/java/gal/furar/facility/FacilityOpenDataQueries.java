// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * API pública síncrona das lecturas que precisan os datos abertos e a súa ocupación (ADR-0034,
 * punto 1; A19): sempre no concello actual, cun número fixo de sentenzas e só o que xa é público
 * (agás as partes retiradas, que só serven para contar as partes vivas dun día). Non comproba
 * permisos.
 */
public interface FacilityOpenDataQueries {

  /** Días como moito nunha lectura de {@link #publicClosures}. */
  int MAX_CLOSURE_DAYS = 1200;

  /**
   * As instalacións do listado público (nin en borrador nin de baixa) cos usos que mostra a súa
   * ficha (non retirados e «sempre» ou con algunha data de hoxe en diante).
   *
   * @param today o día de hoxe en Europe/Madrid (a tempada vixente e os usos que seguen)
   */
  PublicFacilityData publicData(LocalDate today);

  /**
   * Os días enteiros pechados das instalacións do listado público por unha excepción pechada ou un
   * bloqueo de todo o día, un por fila, por data e instalación.
   *
   * @throws IllegalArgumentException se o rango pasa de {@value #MAX_CLOSURE_DAYS} días
   */
  List<ClosureDay> publicClosures(LocalDate from, LocalDate to);

  /** Os festivos do catálogo entre as dúas datas (incluídas), por data. */
  List<HolidayDay> holidays(LocalDate from, LocalDate to);

  /** As partes retiradas deses usos, con cando se retiraron. */
  List<ArchivedPart> archivedParts(Collection<UUID> useIds);

  /** Un día pechado dunha instalación, co motivo que escribiu o persoal, se o hai. */
  record ClosureDay(LocalDate date, UUID facilityId, @Nullable String reason) {}

  /** Un festivo do catálogo. */
  record HolidayDay(LocalDate date, String nameGl, String nameEs) {}

  /** Unha parte retirada dun uso. */
  record ArchivedPart(UUID useId, UUID partId, Instant archivedAt) {}
}
