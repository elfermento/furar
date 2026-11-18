// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.opendata.application.service;

import gal.furar.facility.FacilityOpenDataQueries;
import gal.furar.facility.PublicFacilityData;
import gal.furar.indicators.IndicatorsQueries;
import gal.furar.indicators.IndicatorsQueries.DayQuarters;
import gal.furar.municipality.MunicipalityOpenData;
import gal.furar.municipality.MunicipalityQueries;
import gal.furar.opendata.application.port.out.OpenDataStore;
import gal.furar.opendata.application.port.out.OpenDataStore.DatasetState;
import gal.furar.opendata.application.port.out.OpenDataStore.StoredDay;
import gal.furar.opendata.domain.Dataset;
import gal.furar.opendata.domain.OccupancyRange;
import gal.furar.shared.tenancy.MunicipalityDirectory;
import gal.furar.shared.tenancy.PlatformTask;
import gal.furar.shared.tenancy.TenantContext;
import gal.furar.shared.tenancy.TenantRef;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * A tarefa nocturna dos datos abertos (ADR-0034, puntos 5 e 6): en cada concello, co seu {@link
 * TenantContext#runAs} e as súas transaccións, calcula a ocupación dos días que faltan ata onte (un
 * anaco de ata 31 días por transacción, que sobe {@code calculated_through} no mesmo commit), borra
 * a que xa non se publica e anota o día no que cambia cada conxunto. Co interruptor apagado, borra
 * as súas filas. Un fallo nun concello non para os demais; no rexistro técnico, só o tipo do erro.
 */
@Service
@PlatformTask
public class OpenDataRefreshService {

  /** Días como moito en cada anaco da ocupación. */
  static final int CHUNK_DAYS = IndicatorsQueries.MAX_DAYS;

  private static final Logger LOG = LoggerFactory.getLogger(OpenDataRefreshService.class);

  private final MunicipalityDirectory directory;
  private final MunicipalityQueries municipalities;
  private final FacilityOpenDataQueries facilities;
  private final IndicatorsQueries indicators;
  private final OpenDataStore store;
  private final TransactionTemplate tx;
  private final Clock clock;

  public OpenDataRefreshService(
      MunicipalityDirectory directory,
      MunicipalityQueries municipalities,
      FacilityOpenDataQueries facilities,
      IndicatorsQueries indicators,
      OpenDataStore store,
      PlatformTransactionManager transactions,
      Clock clock) {
    this.directory = directory;
    this.municipalities = municipalities;
    this.facilities = facilities;
    this.indicators = indicators;
    this.store = store;
    this.tx = new TransactionTemplate(transactions);
    this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    this.clock = clock;
  }

  @Scheduled(cron = "${furar.opendata.refresh-cron:0 15 3 * * *}")
  void scheduled() {
    refresh();
  }

  /** Percorre todos os concellos; devolve en cantos fallou. */
  public int refresh() {
    // Unha vez por execución: unha tarefa que cruza a medianoite non cambia de día a metade.
    LocalDate today = LocalDate.now(clock.withZone(OccupancyRange.ZONE));
    List<TenantRef> all = TenantContext.runAsPlatform(directory::all);
    int failed = 0;
    for (TenantRef municipality : all) {
      try {
        TenantContext.runAs(municipality, () -> municipality(municipality, today));
      } catch (RuntimeException e) {
        failed++;
        LOG.warn(
            "open-data refresh failed municipality={} error={}",
            municipality.id(),
            e.getClass().getName());
      }
    }
    if (failed > 0) {
      LOG.info("open-data refresh municipalities={} failed={}", all.size(), failed);
    }
    return failed;
  }

  /** Un concello, xa no seu contexto. */
  void municipality(TenantRef municipality, LocalDate today) {
    boolean enabled =
        municipalities.openData(municipality.id()).map(MunicipalityOpenData::enabled).orElse(false);
    if (!enabled) {
      tx.executeWithoutResult(s -> store.deleteAll());
      return;
    }
    LocalDate yesterday = OccupancyRange.availableTo(today);
    LocalDate oldest = OccupancyRange.availableFrom(today);
    PublicFacilityData data =
        Objects.requireNonNull(tx.execute(s -> facilities.publicData(today)), "publicData");
    List<UUID> shown = OpenDataService.shownOccupancy(data);
    tx.executeWithoutResult(
        s -> {
          store.deleteOccupancyExcept(shown);
          store.deleteOccupancyBefore(oldest);
        });

    DatasetState previous =
        Objects.requireNonNull(tx.execute(s -> store.datasets()), "datasets")
            .get(Dataset.OCCUPANCY);
    LocalDate from =
        previous == null || previous.calculatedThrough() == null
            ? oldest
            : max(previous.calculatedThrough().plusDays(1), oldest);
    while (!from.isAfter(yesterday)) {
      LocalDate start = from;
      LocalDate end = min(start.plusDays(CHUNK_DAYS - 1L), yesterday);
      tx.executeWithoutResult(
          s -> {
            store.deleteOccupancyBetween(start, end);
            store.insertOccupancy(
                indicators.quarterOccupancy(start, end, shown).stream()
                    .map(OpenDataRefreshService::stored)
                    .toList());
            long rows = store.countOccupancy(oldest, end, shown);
            save(Dataset.OCCUPANCY, end + "|" + rows, today, end);
          });
      from = end.plusDays(1);
    }

    tx.executeWithoutResult(
        s -> {
          save(Dataset.FACILITIES, OpenDataService.sha256(data.toString()), today, null);
          LocalDate first = LocalDate.of(today.getYear() - 1, 1, 1);
          LocalDate last = LocalDate.of(today.getYear() + 1, 12, 31);
          String closures =
              facilities.holidays(first, last).toString()
                  + facilities.publicClosures(first, last).toString();
          save(Dataset.CLOSURES, OpenDataService.sha256(closures), today, null);
          // Sen días que calcular, a ocupación tamén ten fila desde a primeira noite.
          Map<Dataset, DatasetState> states = store.datasets();
          if (!states.containsKey(Dataset.OCCUPANCY)) {
            save(Dataset.OCCUPANCY, yesterday + "|0", today, yesterday);
          }
        });
  }

  /** Garda a pegada e, se cambiou, o día da tarefa como «Actualizado o…». */
  private void save(
      Dataset dataset, String checksum, LocalDate today, @Nullable LocalDate calculatedThrough) {
    DatasetState current = store.datasets().get(dataset);
    if (current != null
        && current.checksum().equals(checksum)
        && Objects.equals(current.calculatedThrough(), calculatedThrough)) {
      return;
    }
    LocalDate changedOn =
        current != null && current.checksum().equals(checksum) ? current.changedOn() : today;
    store.saveDataset(dataset, new DatasetState(checksum, changedOn, calculatedThrough));
  }

  private static StoredDay stored(DayQuarters d) {
    return new StoredDay(d.facilityId(), d.day(), d.quarters(), d.uses(), d.quarterUses());
  }

  private static LocalDate max(LocalDate a, LocalDate b) {
    return a.isAfter(b) ? a : b;
  }

  private static LocalDate min(LocalDate a, LocalDate b) {
    return a.isBefore(b) ? a : b;
  }
}
