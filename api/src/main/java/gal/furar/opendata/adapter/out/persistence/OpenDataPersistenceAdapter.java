// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.opendata.adapter.out.persistence;

import gal.furar.opendata.application.port.out.OpenDataStore;
import gal.furar.opendata.domain.Dataset;
import gal.furar.shared.tenancy.TenantScopedPersistenceAdapter;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** As táboas dos datos abertos no concello actual: falla sen concello ou sen transacción. */
@Component
class OpenDataPersistenceAdapter extends TenantScopedPersistenceAdapter implements OpenDataStore {

  private final OpenDataOccupancySql occupancy;
  private final OpenDataDatasetSql datasets;

  OpenDataPersistenceAdapter(OpenDataOccupancySql occupancy, OpenDataDatasetSql datasets) {
    this.occupancy = occupancy;
    this.datasets = datasets;
  }

  @Override
  public void deleteAll() {
    UUID municipality = tenant().id();
    occupancy.deleteAll(municipality);
    datasets.deleteAll(municipality);
  }

  @Override
  public void deleteOccupancyExcept(Collection<UUID> facilityIds) {
    occupancy.deleteExcept(tenant().id(), facilityIds);
  }

  @Override
  public void deleteOccupancyBetween(LocalDate from, LocalDate to) {
    occupancy.deleteBetween(tenant().id(), from, to);
  }

  @Override
  public void deleteOccupancyBefore(LocalDate day) {
    occupancy.deleteBefore(tenant().id(), day);
  }

  @Override
  public void insertOccupancy(List<StoredDay> days) {
    UUID municipality = tenant().id();
    days.forEach(d -> occupancy.insert(municipality, d));
  }

  @Override
  public List<StoredDay> occupancy(LocalDate from, LocalDate to, Collection<UUID> facilityIds) {
    return occupancy.find(tenant().id(), from, to, facilityIds);
  }

  @Override
  public long countOccupancy(LocalDate from, LocalDate to, Collection<UUID> facilityIds) {
    return occupancy.count(tenant().id(), from, to, facilityIds);
  }

  @Override
  public QuarterCount quarterCount(LocalDate from, LocalDate to) {
    return occupancy.quarters(tenant().id(), from, to);
  }

  @Override
  public Map<Dataset, DatasetState> datasets() {
    return datasets.all(tenant().id());
  }

  @Override
  public void saveDataset(Dataset dataset, DatasetState state) {
    datasets.save(tenant().id(), dataset, state);
  }
}
