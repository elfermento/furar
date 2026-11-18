// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.opendata.application.service;

import gal.furar.facility.FacilityOpenDataQueries;
import gal.furar.facility.FacilityQueries;
import gal.furar.facility.PublicCustomFields;
import gal.furar.facility.PublicFacilityData;
import gal.furar.facility.UseSummary;
import gal.furar.municipality.MunicipalityOpenData;
import gal.furar.municipality.MunicipalityQueries;
import gal.furar.opendata.application.port.in.CatalogRecord;
import gal.furar.opendata.application.port.in.ClosuresFile;
import gal.furar.opendata.application.port.in.FacilitiesFile;
import gal.furar.opendata.application.port.in.OccupancySink;
import gal.furar.opendata.application.port.in.OpenDataOverview;
import gal.furar.opendata.application.port.in.OpenDataPublisher;
import gal.furar.opendata.application.port.in.ReadOpenDataUseCase;
import gal.furar.opendata.application.port.in.Scope;
import gal.furar.opendata.application.port.out.OpenDataStore;
import gal.furar.opendata.application.port.out.OpenDataStore.DatasetState;
import gal.furar.opendata.application.port.out.OpenDataStore.QuarterCount;
import gal.furar.opendata.application.port.out.OpenDataStore.StoredDay;
import gal.furar.opendata.domain.Dataset;
import gal.furar.opendata.domain.OccupancyRange;
import gal.furar.opendata.domain.OpenDataError;
import gal.furar.opendata.domain.QuarterRow;
import gal.furar.shared.domain.CommonError;
import gal.furar.shared.domain.DomainException;
import gal.furar.shared.security.PublicUseCase;
import gal.furar.shared.tenancy.CatalogMunicipalities;
import gal.furar.shared.tenancy.TenantContext;
import gal.furar.shared.tenancy.TenantRef;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Os datos abertos sen sesión (ADR-0034). Nun concello, o seu (co {@code TenantContext} do filtro);
 * en todos, concello a concello, cada un no seu {@link TenantContext#runAs} e nas súas transaccións
 * (punto 8): nunca unha lectura que vexa dous concellos. Por iso os métodos non abren transacción:
 * cada lectura vai na súa, curta e só de lectura; a ocupación, un mes por transacción, escrita
 * despois de pechala (punto 20).
 */
@Service
public class OpenDataService implements ReadOpenDataUseCase {

  /** Meses como moito nunha descarga da ocupación dun concello e de todos. */
  static final int MAX_MONTHS = 12;

  static final int MAX_MONTHS_ALL = 1;

  /** Bytes aproximados dunha fila da ocupación (o tamaño estimado do diálogo). */
  static final long CSV_ROW_BYTES = 125;

  static final long JSON_ROW_BYTES = 230;

  private final MunicipalityQueries municipalities;
  private final CatalogMunicipalities catalog;
  private final FacilityOpenDataQueries facilities;
  private final FacilityQueries facilityQueries;
  private final OpenDataStore store;
  private final TransactionTemplate reads;
  private final Semaphore downloads;
  private final String publisher;
  private final String publisherDir3;
  private final Clock clock;

  public OpenDataService(
      MunicipalityQueries municipalities,
      CatalogMunicipalities catalog,
      FacilityOpenDataQueries facilities,
      FacilityQueries facilityQueries,
      OpenDataStore store,
      PlatformTransactionManager transactions,
      @Value("${furar.opendata.occupancy-downloads:2}") int downloads,
      @Value("${furar.opendata.publisher:}") String publisher,
      @Value("${furar.opendata.publisher-dir3:}") String publisherDir3,
      Clock clock) {
    this.municipalities = municipalities;
    this.catalog = catalog;
    this.facilities = facilities;
    this.facilityQueries = facilityQueries;
    this.store = store;
    this.reads = new TransactionTemplate(transactions);
    this.reads.setReadOnly(true);
    this.reads.setTimeout(10);
    this.reads.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    this.downloads = new Semaphore(Math.max(1, downloads));
    this.publisher = publisher == null ? "" : publisher.strip();
    this.publisherDir3 = publisherDir3 == null ? "" : publisherDir3.strip();
    this.clock = clock;
  }

  @Override
  @PublicUseCase
  public OpenDataOverview overview(Scope scope) {
    LocalDate today = today();
    LocalDate yesterday = OccupancyRange.availableTo(today);
    OccupancyRange last = OccupancyRange.lastFullMonth(today);
    if (scope == Scope.MUNICIPALITY) {
      OpenDataPublisher current = current();
      Overview o = read(() -> overviewOf(today, true));
      return new OpenDataOverview(
          o.updatedOn(),
          OccupancyRange.availableFrom(today),
          yesterday,
          last.from(),
          last.to(),
          MAX_MONTHS,
          o.rowsPerDay() * CSV_ROW_BYTES,
          o.rowsPerDay() * JSON_ROW_BYTES,
          current.dir3Code() != null,
          1,
          current.legalEntityName(),
          o.customFields());
    }
    List<OpenDataPublisher> all = published(today);
    Map<Dataset, LocalDate> updatedOn = new EnumMap<>(Dataset.class);
    long rowsPerDay = 0;
    for (OpenDataPublisher p : all) {
      Overview o = runAs(p, () -> read(() -> overviewOf(today, false)));
      o.updatedOn().forEach((d, day) -> updatedOn.merge(d, day, (a, b) -> a.isAfter(b) ? a : b));
      rowsPerDay += o.rowsPerDay();
    }
    return new OpenDataOverview(
        updatedOn,
        OccupancyRange.availableFrom(today),
        yesterday,
        last.from(),
        last.to(),
        MAX_MONTHS_ALL,
        rowsPerDay * CSV_ROW_BYTES,
        rowsPerDay * JSON_ROW_BYTES,
        !all.isEmpty() && allCatalog(),
        all.size(),
        null,
        List.of());
  }

  @Override
  @PublicUseCase
  public FacilitiesFile facilities(Scope scope) {
    LocalDate today = today();
    List<FacilitiesFile.Part> parts = new ArrayList<>();
    LocalDate updated = null;
    for (OpenDataPublisher p : publishers(scope, today)) {
      PublicFacilityData data = runAs(p, () -> read(() -> facilities.publicData(today)));
      parts.add(new FacilitiesFile.Part(p, data));
      updated =
          latest(updated, runAs(p, () -> read(() -> changedOn(Dataset.FACILITIES))).orElse(null));
    }
    return new FacilitiesFile(parts, updated);
  }

  @Override
  @PublicUseCase
  public ClosuresFile closures(Scope scope) {
    LocalDate today = today();
    List<ClosuresFile.Part> parts = new ArrayList<>();
    LocalDate updated = null;
    for (OpenDataPublisher p : publishers(scope, today)) {
      List<ClosuresFile.Row> rows = runAs(p, () -> read(() -> closureRows(p, today)));
      parts.add(new ClosuresFile.Part(p, rows));
      updated =
          latest(updated, runAs(p, () -> read(() -> changedOn(Dataset.CLOSURES))).orElse(null));
    }
    return new ClosuresFile(parts, updated);
  }

  @Override
  @PublicUseCase
  public OccupancyRange occupancyRange(Scope scope, @Nullable String from, @Nullable String to) {
    if (scope == Scope.MUNICIPALITY) {
      current();
    }
    return OccupancyRange.of(
        from, to, today(), scope == Scope.MUNICIPALITY ? MAX_MONTHS : MAX_MONTHS_ALL);
  }

  @Override
  @PublicUseCase
  public String occupancyTag(OccupancyRange range) {
    current();
    LocalDate today = today();
    String state =
        read(
            () -> {
              List<UUID> ids = shownOccupancy(facilities.publicData(today));
              DatasetState occupancy = store.datasets().get(Dataset.OCCUPANCY);
              return (occupancy == null ? "-" : String.valueOf(occupancy.calculatedThrough()))
                  + "|"
                  + store.countOccupancy(range.from(), range.to(), ids)
                  + "|"
                  + ids.stream().sorted().map(UUID::toString).collect(Collectors.joining(","));
            });
    return sha256(state + "|" + range.from() + "|" + range.to());
  }

  @Override
  @PublicUseCase
  public void occupancy(Scope scope, OccupancyRange range, OccupancySink sink) {
    LocalDate today = today();
    List<OpenDataPublisher> all = publishers(scope, today);
    if (!downloads.tryAcquire()) {
      throw new DomainException(CommonError.RATE_LIMITED);
    }
    try {
      LocalDate updated = null;
      for (OpenDataPublisher p : all) {
        updated =
            latest(updated, runAs(p, () -> read(() -> changedOn(Dataset.OCCUPANCY))).orElse(null));
      }
      sink.begin(all, updated);
      for (OpenDataPublisher p : all) {
        List<UUID> ids = runAs(p, () -> read(() -> shownOccupancy(facilities.publicData(today))));
        if (ids.isEmpty()) {
          continue;
        }
        for (LocalDate start = range.from();
            !start.isAfter(range.to());
            start = start.withDayOfMonth(1).plusMonths(1)) {
          LocalDate from = start;
          LocalDate monthEnd = start.withDayOfMonth(start.lengthOfMonth());
          LocalDate to = monthEnd.isBefore(range.to()) ? monthEnd : range.to();
          Chunk chunk = runAs(p, () -> read(() -> chunk(from, to, ids)));
          sink.write(p, chunk.rows(), chunk.uses());
        }
      }
    } finally {
      downloads.release();
    }
  }

  @Override
  @PublicUseCase
  public Optional<CatalogRecord> catalog(Scope scope) {
    LocalDate today = today();
    if (scope == Scope.MUNICIPALITY) {
      OpenDataPublisher p = current();
      if (p.dir3Code() == null) {
        return Optional.empty();
      }
      Map<Dataset, LocalDate> updatedOn = read(() -> updatedOn(store.datasets()));
      return Optional.of(
          new CatalogRecord(
              p.slug(),
              p.attribution(gal.furar.shared.domain.Language.GL),
              p.dir3Code(),
              p.since(),
              updatedOn,
              OccupancyRange.availableFrom(today),
              OccupancyRange.availableTo(today)));
    }
    if (!allCatalog()) {
      return Optional.empty();
    }
    List<OpenDataPublisher> all = published(today);
    if (all.isEmpty()) {
      return Optional.empty();
    }
    Map<Dataset, LocalDate> updatedOn = new EnumMap<>(Dataset.class);
    Instant issued = null;
    for (OpenDataPublisher p : all) {
      runAs(p, () -> read(() -> updatedOn(store.datasets())))
          .forEach((d, day) -> updatedOn.merge(d, day, (a, b) -> a.isAfter(b) ? a : b));
      issued = issued == null || p.since().isBefore(issued) ? p.since() : issued;
    }
    return Optional.of(
        new CatalogRecord(
            null,
            publisher,
            publisherDir3,
            Objects.requireNonNull(issued),
            updatedOn,
            OccupancyRange.availableFrom(today),
            OccupancyRange.availableTo(today)));
  }

  /** As instalacións que entran na ocupación: activas e cos ocos á vista (puntos 3 e 7). */
  static List<UUID> shownOccupancy(PublicFacilityData data) {
    return data.facilities().stream()
        .filter(f -> !f.temporarilyClosed() && (f.onlineBooking() || f.showFreeSlots()))
        .map(PublicFacilityData.Item::id)
        .toList();
  }

  /**
   * Os peches dun concello: os festivos do catálogo e os días enteiros pechados das instalacións,
   * do 1 de xaneiro do ano anterior ao 31 de decembro do seguinte. Os motivos dos peches anteriores
   * ao día en que empezou a publicar non saen (ADR-0034).
   */
  private List<ClosuresFile.Row> closureRows(OpenDataPublisher p, LocalDate today) {
    LocalDate from = LocalDate.of(today.getYear() - 1, 1, 1);
    LocalDate to = LocalDate.of(today.getYear() + 1, 12, 31);
    LocalDate cut = LocalDate.ofInstant(p.since(), OccupancyRange.ZONE);
    List<ClosuresFile.Row> rows = new ArrayList<>();
    facilities
        .holidays(from, to)
        .forEach(h -> rows.add(new ClosuresFile.Row(h.date(), true, null, h.nameGl(), h.nameEs())));
    facilities
        .publicClosures(from, to)
        .forEach(
            c -> {
              String reason = c.date().isBefore(cut) ? null : c.reason();
              rows.add(new ClosuresFile.Row(c.date(), false, c.facilityId(), reason, reason));
            });
    rows.sort(
        Comparator.comparing(ClosuresFile.Row::date)
            .thenComparing(r -> !r.holiday())
            .thenComparing(r -> r.facilityId() == null ? "" : r.facilityId().toString()));
    return rows;
  }

  private Chunk chunk(LocalDate from, LocalDate to, List<UUID> ids) {
    List<QuarterRow> rows = new ArrayList<>();
    Set<UUID> useIds = new LinkedHashSet<>();
    for (StoredDay day : store.occupancy(from, to, ids)) {
      List<QuarterRow> expanded =
          QuarterRow.expand(
              day.facilityId(), day.day(), day.quarters(), day.uses(), day.quarterUses());
      expanded.stream().map(QuarterRow::useId).filter(Objects::nonNull).forEach(useIds::add);
      rows.addAll(expanded);
    }
    rows.sort(
        Comparator.comparing(QuarterRow::facilityId)
            .thenComparing(QuarterRow::day)
            .thenComparing(r -> r.start().toInstant()));
    Map<UUID, OccupancySink.UseLabel> uses =
        useIds.isEmpty()
            ? Map.of()
            : facilityQueries.usesById(useIds).stream()
                .collect(
                    Collectors.toMap(
                        UseSummary::id, u -> new OccupancySink.UseLabel(u.name(), u.rentalMode())));
    return new Chunk(rows, uses);
  }

  private record Chunk(List<QuarterRow> rows, Map<UUID, OccupancySink.UseLabel> uses) {}

  private record Overview(
      Map<Dataset, LocalDate> updatedOn,
      long rowsPerDay,
      List<PublicCustomFields.Definition> customFields) {}

  private Overview overviewOf(LocalDate today, boolean withFields) {
    LocalDate yesterday = OccupancyRange.availableTo(today);
    QuarterCount count = store.quarterCount(yesterday.minusDays(29), yesterday);
    long perDay = count.days() == 0 ? 0 : count.quarters() / count.days();
    return new Overview(
        updatedOn(store.datasets()),
        perDay,
        withFields ? facilities.publicData(today).customFields() : List.of());
  }

  private static Map<Dataset, LocalDate> updatedOn(Map<Dataset, DatasetState> states) {
    Map<Dataset, LocalDate> result = new EnumMap<>(Dataset.class);
    states.forEach((d, s) -> result.put(d, s.changedOn()));
    return result;
  }

  private Optional<LocalDate> changedOn(Dataset dataset) {
    return Optional.ofNullable(store.datasets().get(dataset)).map(DatasetState::changedOn);
  }

  private static @Nullable LocalDate latest(@Nullable LocalDate a, @Nullable LocalDate b) {
    if (a == null) {
      return b;
    }
    return b == null || a.isAfter(b) ? a : b;
  }

  private boolean allCatalog() {
    return !publisher.isEmpty() && !publisherDir3.isEmpty();
  }

  /** O concello actual, se publica; se non, 404 {@code opendata.not-published}. */
  private OpenDataPublisher current() {
    TenantRef tenant = TenantContext.require();
    MunicipalityOpenData data =
        municipalities
            .openData(tenant.id())
            .filter(MunicipalityOpenData::enabled)
            .orElseThrow(() -> new DomainException(OpenDataError.NOT_PUBLISHED));
    return publisher(tenant.id(), data);
  }

  private List<OpenDataPublisher> publishers(Scope scope, LocalDate today) {
    return scope == Scope.MUNICIPALITY ? List.of(current()) : published(today);
  }

  /**
   * Os concellos do conxunto de todos (punto 8): os da portada común (datos legais completos e
   * algunha instalación activa á vista) co interruptor aceso, por nome.
   */
  private List<OpenDataPublisher> published(LocalDate today) {
    List<OpenDataPublisher> result = new ArrayList<>();
    int page = 0;
    CatalogMunicipalities.Page listed;
    do {
      listed = catalog.page(page++, CatalogMunicipalities.MAX_PAGE_SIZE);
      for (CatalogMunicipalities.Listed m : listed.items()) {
        if (!m.openData()) {
          continue;
        }
        Optional<OpenDataPublisher> p =
            TenantContext.runAs(
                m.tenant(),
                () ->
                    municipalities
                        .openData(m.tenant().id())
                        .filter(MunicipalityOpenData::enabled)
                        .filter(d -> open(read(() -> facilities.publicData(today))))
                        .map(d -> publisher(m.tenant().id(), d)));
        p.ifPresent(result::add);
      }
    } while (listed.hasNext());
    return result;
  }

  /** Algunha instalación activa á vista: o mesmo criterio ca a portada común (ADR-0030). */
  private static boolean open(PublicFacilityData data) {
    return data.facilities().stream().anyMatch(f -> !f.temporarilyClosed());
  }

  private static OpenDataPublisher publisher(UUID id, MunicipalityOpenData data) {
    return new OpenDataPublisher(
        id,
        data.slug(),
        data.name(),
        Objects.requireNonNull(data.since(), "since"),
        data.legalEntityName(),
        data.dir3Code());
  }

  private static <T> T runAs(OpenDataPublisher p, Supplier<T> work) {
    Optional<TenantRef> current = TenantContext.current();
    if (current.isPresent() && current.get().id().equals(p.id())) {
      return work.get();
    }
    return TenantContext.runAs(new TenantRef(p.id(), p.slug()), work);
  }

  private <T> T read(Supplier<T> work) {
    return Objects.requireNonNull(reads.execute(status -> work.get()), "read");
  }

  private LocalDate today() {
    return LocalDate.now(clock.withZone(OccupancyRange.ZONE));
  }

  static String sha256(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
