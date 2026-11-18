// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.indicators.application.service;

import gal.furar.indicators.IndicatorsPermission;
import gal.furar.indicators.application.port.in.IndicatorsPanel;
import gal.furar.indicators.application.port.in.IndicatorsPanel.Kpi;
import gal.furar.indicators.application.port.in.IndicatorsPanel.Kpis;
import gal.furar.indicators.application.port.in.IndicatorsPanel.Occupancy;
import gal.furar.indicators.application.port.in.IndicatorsPanel.Point;
import gal.furar.indicators.application.port.in.IndicatorsQuery;
import gal.furar.indicators.application.port.in.PlatformIndicators;
import gal.furar.indicators.application.port.in.PlatformIndicators.MunicipalityFigures;
import gal.furar.indicators.application.port.in.PlatformIndicators.Totals;
import gal.furar.indicators.application.port.in.ReadPlatformIndicatorsUseCase;
import gal.furar.indicators.domain.IndicatorsError;
import gal.furar.indicators.domain.PeriodKind;
import gal.furar.indicators.domain.ReportPeriod;
import gal.furar.municipality.MunicipalityQueries;
import gal.furar.shared.domain.DomainException;
import gal.furar.shared.security.RequiresPermission;
import gal.furar.shared.tenancy.MunicipalityDirectory;
import gal.furar.shared.tenancy.PlatformTask;
import gal.furar.shared.tenancy.TenantContext;
import gal.furar.shared.tenancy.TenantRef;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Os indicadores da plataforma (ADR-0026, revisión do 2026-11-11; ADR-0006, lecturas entre
 * concellos). Non hai ningunha consulta sobre varios concellos: percorre os concellos ({@link
 * MunicipalityDirectory}, unha táboa da plataforma) e calcula o panel de cada un co mesmo código ca
 * a súa páxina de indicadores, dentro do seu {@link TenantContext#runAs} e nunha transacción de só
 * lectura propia, coa súa RLS. Das cifras de cada concello quedan só as catro sumables e as persoas
 * distintas da súa fila; os totais suman as catro ({@link PlatformTotals}).
 *
 * <p><b>Custo.</b> Un panel por concello: as sentenzas fixas do panel (arredor de 25, ADR-0026
 * punto 14) por concello e petición, sen caché entre peticións. Con poucas decenas de concellos son
 * uns centos de sentenzas sobre índices; o CSV e o PDF saen no navegador da mesma resposta, sen
 * petición nova. Cada concello ten o tempo máximo do panel ({@value
 * IndicatorsService#TIMEOUT_SECONDS} s); con máis de {@value #MAX_MUNICIPALITIES} concellos non se
 * calcula (409). Se un concello falla, falla a petición: uns totais sen un concello serían falsos.
 *
 * <p>Sen {@code AuditLog}, como o panel do concello: só cifras agregadas.
 */
@Service
@PlatformTask
public class PlatformIndicatorsService implements ReadPlatformIndicatorsUseCase {

  /** Concellos como moito nunha petición. */
  static final int MAX_MUNICIPALITIES = 100;

  private final IndicatorsService panels;
  private final MunicipalityDirectory directory;
  private final MunicipalityQueries municipalities;
  private final TransactionTemplate tx;
  private final Clock clock;

  public PlatformIndicatorsService(
      IndicatorsService panels,
      MunicipalityDirectory directory,
      MunicipalityQueries municipalities,
      PlatformTransactionManager transactions,
      Clock clock) {
    this.panels = panels;
    this.directory = directory;
    this.municipalities = municipalities;
    this.tx = new TransactionTemplate(transactions);
    this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    this.tx.setReadOnly(true);
    this.tx.setTimeout(IndicatorsService.TIMEOUT_SECONDS);
    this.clock = clock;
  }

  @Override
  @RequiresPermission(IndicatorsPermission.Code.PLATFORM_VIEW)
  public PlatformIndicators read(
      PeriodKind kind, @Nullable LocalDate date, @Nullable LocalDate from, @Nullable LocalDate to) {
    IndicatorsQuery query = new IndicatorsQuery(kind, date, from, to, null, List.of());
    // O mesmo día para todos os concellos, e o período validado unha vez, antes de ler nada.
    LocalDate today = LocalDate.now(clock.withZone(ReportPeriod.ZONE));
    ReportPeriod period = IndicatorsService.period(query, today);
    // A data fixada: unha petición que cruza a medianoite non cambia de período a metade.
    IndicatorsQuery anchored =
        new IndicatorsQuery(kind, date == null ? today : date, from, to, null, List.of());
    List<TenantRef> all = TenantContext.runAsPlatform(directory::all);
    if (all.size() > MAX_MUNICIPALITIES) {
      throw new DomainException(
          IndicatorsError.TOO_MANY_MUNICIPALITIES, Map.of("max", MAX_MUNICIPALITIES));
    }
    List<Kpis> kpis = new ArrayList<>(all.size());
    List<MunicipalityFigures> rows = new ArrayList<>(all.size());
    for (TenantRef municipality : all) {
      Computed computed =
          TenantContext.runAs(
              municipality,
              () -> Objects.requireNonNull(tx.execute(s -> compute(municipality, anchored))));
      kpis.add(computed.panel().kpis());
      rows.add(computed.row());
    }
    return new PlatformIndicators(
        period,
        period.previous(),
        period.comparison(today),
        PlatformTotals.sum(kpis, empty(period, today)),
        Names.sorted(rows, MunicipalityFigures::name));
  }

  private record Computed(IndicatorsPanel panel, MunicipalityFigures row) {}

  /** Un concello, xa no seu contexto e na súa transacción. */
  private Computed compute(TenantRef municipality, IndicatorsQuery query) {
    IndicatorsPanel panel = panels.compute(query);
    Kpis k = panel.kpis();
    String name = municipalities.name(municipality.id()).orElse("");
    return new Computed(
        panel,
        new MunicipalityFigures(
            municipality.id(),
            Objects.requireNonNullElse(municipality.slug(), ""),
            name,
            k.bookings().value(),
            k.occupancy().value(),
            k.reservedMinutes().value(),
            k.people().value(),
            k.paidCents().value()));
  }

  /** Sen ningún concello: ceros, cos tramos da evolución do período. */
  static Totals empty(ReportPeriod period, LocalDate today) {
    return new Totals(
        zero(period, today, p -> 0L),
        zero(period, today, p -> Occupancy.NONE),
        zero(period, today, p -> 0.0),
        zero(period, today, p -> 0L));
  }

  private static <T> Kpi<T> zero(
      ReportPeriod period, LocalDate today, Function<ReportPeriod, T> value) {
    T none = value.apply(period);
    List<Point<T>> evolution =
        period.evolution().stream()
            .map(p -> new Point<>(p.from(), p.to(), value.apply(p), p.contains(today)))
            .toList();
    return new Kpi<>(none, none, none, none, evolution);
  }
}
