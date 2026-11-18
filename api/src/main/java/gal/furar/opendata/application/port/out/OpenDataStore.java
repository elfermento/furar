// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.opendata.application.port.out;

import gal.furar.opendata.domain.Dataset;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * As dúas táboas do módulo (ADR-0034, puntos 3 e 6), sempre no concello actual e dentro dunha
 * transacción (RLS): a ocupación gardada, un día por fila, e a data de cambio de cada conxunto.
 */
public interface OpenDataStore {

  /** Borra todo o do concello (datos abertos apagados). */
  void deleteAll();

  /** Borra a ocupación das instalacións que non están na lista. */
  void deleteOccupancyExcept(Collection<UUID> facilityIds);

  /** Borra a ocupación entre as dúas datas (incluídas). */
  void deleteOccupancyBetween(LocalDate from, LocalDate to);

  /** Borra a ocupación anterior a ese día. */
  void deleteOccupancyBefore(LocalDate day);

  /** Garda días novos (sen pisar: unha fila que xa existe fai fallar a transacción). */
  void insertOccupancy(List<StoredDay> days);

  /** Os días gardados entre as dúas datas (incluídas) desas instalacións, por instalación e día. */
  List<StoredDay> occupancy(LocalDate from, LocalDate to, Collection<UUID> facilityIds);

  /** Cantas filas (días) hai gardadas desas instalacións entre as dúas datas. */
  long countOccupancy(LocalDate from, LocalDate to, Collection<UUID> facilityIds);

  /** Os tramos con valor e os días distintos gardados entre as dúas datas (o tamaño estimado). */
  QuarterCount quarterCount(LocalDate from, LocalDate to);

  /** O estado dos conxuntos que xa teñen fila. */
  Map<Dataset, DatasetState> datasets();

  /** Garda o estado dun conxunto (alta ou cambio). */
  void saveDataset(Dataset dataset, DatasetState state);

  /**
   * Un día gardado.
   *
   * @param quarters 0–100 ou nulo por tramo
   * @param uses os usos do día
   * @param quarterUses posición desde 1 en {@code uses} ou nulo, por tramo
   */
  record StoredDay(
      UUID facilityId,
      LocalDate day,
      List<Integer> quarters,
      List<UUID> uses,
      List<Integer> quarterUses) {

    public StoredDay {
      quarters = Collections.unmodifiableList(new ArrayList<>(quarters));
      uses = List.copyOf(uses);
      quarterUses = Collections.unmodifiableList(new ArrayList<>(quarterUses));
    }
  }

  /**
   * O estado dun conxunto.
   *
   * @param checksum a pegada do último cálculo
   * @param changedOn o día da tarefa que viu o cambio
   * @param calculatedThrough só na ocupación: o último día calculado
   */
  record DatasetState(
      String checksum, LocalDate changedOn, @Nullable LocalDate calculatedThrough) {}

  /** Tramos con valor e días distintos. */
  record QuarterCount(long quarters, long days) {}
}
