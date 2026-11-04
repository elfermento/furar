// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.indicators.domain;

import gal.furar.shared.domain.DomainException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * O período dos indicadores (plan do dominio 3, §1.2.3 e §1.2.5): unha semana ISO, un mes, un
 * trimestre ou un ano naturais, ou un rango libre de 1 a {@value #MAX_CUSTOM_DAYS} días. Datas de
 * Europe/Madrid cos dous extremos incluídos; os límites reais son {@code [desde 00:00, ata+1
 * 00:00)} nesa zona ({@link #start()}, {@link #end()}), así os días de 23 e de 25 horas quedan ben.
 */
public final class ReportPeriod {

  /** A zona de todos os días, semanas, meses e horas dos indicadores. */
  public static final ZoneId ZONE = ZoneId.of("Europe/Madrid");

  /** Máximo dun rango libre (P9: un ano, bisesto incluído). */
  public static final int MAX_CUSTOM_DAYS = 366;

  /** Rango libre: ata 31 días, desagregado por día; ata 120, por semana; despois, por mes. */
  static final int CUSTOM_DAYS_BY_DAY = 31;

  static final int CUSTOM_DAYS_BY_WEEK = 120;

  private static final int EVOLUTION_WEEKS = 12;
  private static final int EVOLUTION_MONTHS = 12;
  private static final int EVOLUTION_QUARTERS = 8;
  private static final int EVOLUTION_YEARS = 5;

  private final PeriodKind kind;
  private final LocalDate from;
  private final LocalDate to;

  private ReportPeriod(PeriodKind kind, LocalDate from, LocalDate to) {
    this.kind = kind;
    this.from = from;
    this.to = to;
  }

  /**
   * A unidade natural ({@code WEEK}, {@code MONTH}, {@code QUARTER} ou {@code YEAR}) que contén a
   * data.
   *
   * @throws IllegalArgumentException con {@code CUSTOM}: o rango libre faise con {@link
   *     #custom(LocalDate, LocalDate)}
   */
  public static ReportPeriod of(PeriodKind kind, LocalDate anchor) {
    Objects.requireNonNull(kind, "kind");
    Objects.requireNonNull(anchor, "anchor");
    PeriodUnit unit = kind.unit();
    if (unit == null) {
      throw new IllegalArgumentException("A custom period needs from and to");
    }
    LocalDate start = unit.start(anchor);
    return new ReportPeriod(kind, start, unit.end(start));
  }

  /**
   * Rango libre de datas, os dous extremos incluídos.
   *
   * @throws DomainException {@link IndicatorsError#PERIOD_INVALID} se falta algunha data, se {@code
   *     to} é anterior a {@code from} ou se pasa de {@value #MAX_CUSTOM_DAYS} días
   */
  public static ReportPeriod custom(@Nullable LocalDate from, @Nullable LocalDate to) {
    if (from == null
        || to == null
        || to.isBefore(from)
        || ChronoUnit.DAYS.between(from, to) + 1 > MAX_CUSTOM_DAYS) {
      throw new DomainException(IndicatorsError.PERIOD_INVALID, Map.of("max", MAX_CUSTOM_DAYS));
    }
    return new ReportPeriod(PeriodKind.CUSTOM, from, to);
  }

  public PeriodKind kind() {
    return kind;
  }

  /** O primeiro día, incluído. */
  public LocalDate from() {
    return from;
  }

  /** O último día, incluído. */
  public LocalDate to() {
    return to;
  }

  /** Número de días, cos dous extremos. */
  public long days() {
    return ChronoUnit.DAYS.between(from, to) + 1;
  }

  /** O instante no que empeza: {@code from} ás 00:00 en Europe/Madrid. */
  public Instant start() {
    return from.atStartOfDay(ZONE).toInstant();
  }

  /** O instante no que acaba (excluído): o día seguinte a {@code to} ás 00:00 en Europe/Madrid. */
  public Instant end() {
    return to.plusDays(1).atStartOfDay(ZONE).toInstant();
  }

  /** Se a data cae no período. */
  public boolean contains(LocalDate date) {
    return !date.isBefore(from) && !date.isAfter(to);
  }

  /**
   * O período co que se compara: a unidade anterior ou, nun rango libre, os mesmos días xusto
   * antes.
   */
  public ReportPeriod previous() {
    PeriodUnit unit = kind.unit();
    if (unit == null) {
      return new ReportPeriod(kind, from.minusDays(days()), from.minusDays(1));
    }
    return of(kind, unit.plus(from, -1));
  }

  /**
   * Os tramos que compara a variación das cifras (ADR-0026, revisión do 2026-10-29). Se hoxe cae no
   * período, «ata hoxe»: do primeiro día a hoxe fronte ao mesmo tramo do anterior, que remata no
   * mesmo día da unidade anterior (a mesma data do mes, do trimestre ou do ano anteriores, co tope
   * no seu último día: o 31 de marzo compárase ata o 28 ou o 29 de febreiro; na semana, o mesmo día
   * da semana anterior; nun rango libre, os mesmos días contados desde o inicio). Se non, os dous
   * períodos enteiros: os pechados non cambian.
   */
  public Comparison comparison(LocalDate today) {
    Objects.requireNonNull(today, "today");
    ReportPeriod before = previous();
    if (!contains(today)) {
      return new Comparison(from, to, before.from, before.to, false);
    }
    PeriodUnit unit = kind.unit();
    LocalDate same = unit == null ? today.minusDays(days()) : unit.plus(today, -1);
    LocalDate previousTo = same.isAfter(before.to) ? before.to : same;
    if (previousTo.isBefore(before.from)) {
      previousTo = before.from;
    }
    return new Comparison(from, today, before.from, previousTo, true);
  }

  /** Se o tramo do día {@code from} ao {@code to} contén hoxe: o cubo en curso dunha serie. */
  public static boolean inProgress(LocalDate from, LocalDate to, LocalDate today) {
    return !today.isBefore(from) && !today.isAfter(to);
  }

  /** A unidade da evolución do modal: a do período ou, nun rango libre, o mes. */
  public PeriodUnit evolutionUnit() {
    PeriodUnit unit = kind.unit();
    return unit == null ? PeriodUnit.MONTH : unit;
  }

  /**
   * A evolución do modal, da máis antiga á máis nova: 12 semanas, 12 meses, 8 trimestres ou 5 anos
   * rematando no período; nun rango libre, 12 meses rematando no mes do seu fin.
   */
  public List<ReportPeriod> evolution() {
    PeriodUnit unit = evolutionUnit();
    int count =
        switch (unit) {
          case WEEK -> EVOLUTION_WEEKS;
          case QUARTER -> EVOLUTION_QUARTERS;
          case YEAR -> EVOLUTION_YEARS;
          default -> EVOLUTION_MONTHS;
        };
    PeriodKind evolutionKind = PeriodKind.valueOf(unit.name());
    LocalDate last = unit.start(to);
    List<ReportPeriod> periods = new ArrayList<>(count);
    for (int i = count - 1; i >= 0; i--) {
      periods.add(of(evolutionKind, unit.plus(last, -i)));
    }
    return List.copyOf(periods);
  }

  /**
   * A desagregación das cancelacións e da ocupación día a día: por día na semana, por semana no mes
   * e no trimestre, por mes no ano; nun rango libre, por día ata 31 días, por semana ata 120 e por
   * mes despois.
   */
  public PeriodUnit breakdownUnit() {
    return switch (kind) {
      case WEEK -> PeriodUnit.DAY;
      case MONTH, QUARTER -> PeriodUnit.WEEK;
      case YEAR -> PeriodUnit.MONTH;
      case CUSTOM -> {
        long days = days();
        if (days <= CUSTOM_DAYS_BY_DAY) {
          yield PeriodUnit.DAY;
        }
        yield days <= CUSTOM_DAYS_BY_WEEK ? PeriodUnit.WEEK : PeriodUnit.MONTH;
      }
    };
  }

  /**
   * O inicio de cada unidade que toca o período, en orde. A primeira pode empezar antes de {@code
   * from} (a semana do día 1 dun mes empeza o luns anterior), como {@code date_trunc} en SQL.
   */
  public List<LocalDate> bucketStarts(PeriodUnit unit) {
    List<LocalDate> starts = new ArrayList<>();
    for (LocalDate d = unit.start(from); !d.isAfter(to); d = unit.plus(d, 1)) {
      starts.add(d);
    }
    return List.copyOf(starts);
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof ReportPeriod p && kind == p.kind && from.equals(p.from) && to.equals(p.to);
  }

  @Override
  public int hashCode() {
    return Objects.hash(kind, from, to);
  }

  @Override
  public String toString() {
    return kind + "[" + from + ".." + to + "]";
  }
}
