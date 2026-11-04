// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.domain;

import gal.furar.shared.domain.DomainException;
import gal.furar.shared.domain.FieldViolation;
import gal.furar.shared.domain.Ids;
import gal.furar.shared.domain.ValidationError;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Uso dunha instalación (agregado; especificación, bloque 1, «Usos»): como se aluga (enteira, por
 * partes con nome ou por prazas), canto dura cada reserva, cando se pode reservar (datas e días da
 * semana) e se bloquea os demais; e, desde o dominio 2, o prezo e as catro normas (ADR-0025, punto
 * 1). Inmutable (A13): as regras van na creación e na edición, e cada cambio devolve unha copia.
 * Dúas regras viven fóra porque miran outros usos ou a base: o nome único na instalación e o das
 * partes no uso (índices da base) e que dous usos que bloquean non se pisen (o servizo, baixo o
 * bloqueo da instalación).
 */
public final class FacilityUse {

  public static final int NAME_MAX = 100;
  public static final int PARTS_MIN = 2;
  public static final int PARTS_MAX = 20;
  public static final int PERIODS_MAX = 50;
  public static final int CAPACITY_MIN = 2;
  public static final int CAPACITY_MAX = 10_000;
  public static final Set<Integer> SLOT_MINUTES = Set.of(30, 60, 90, 120);

  private final UseId id;
  private final FacilityId facilityId;
  private final String name;
  private final RentalMode rentalMode;
  private final @Nullable Integer capacity;
  private final int slotMinutes;
  private final UseAvailability availability;
  private final boolean blocksOthers;
  private final List<UsePart> parts;
  private final List<UsePeriod> periods;
  private final int weekdays;
  private final UsePrice price;
  private final UseRules rules;
  private final @Nullable Instant archivedAt;
  private final @Nullable Long version;
  private final Instant createdAt;
  private final Instant updatedAt;

  private FacilityUse(
      UseId id,
      FacilityId facilityId,
      String name,
      RentalMode rentalMode,
      @Nullable Integer capacity,
      int slotMinutes,
      UseAvailability availability,
      boolean blocksOthers,
      List<UsePart> parts,
      List<UsePeriod> periods,
      int weekdays,
      UsePrice price,
      UseRules rules,
      @Nullable Instant archivedAt,
      @Nullable Long version,
      Instant createdAt,
      Instant updatedAt) {
    this.id = Objects.requireNonNull(id, "id");
    this.facilityId = Objects.requireNonNull(facilityId, "facilityId");
    this.name = Objects.requireNonNull(name, "name");
    this.rentalMode = Objects.requireNonNull(rentalMode, "rentalMode");
    this.capacity = capacity;
    this.slotMinutes = slotMinutes;
    this.availability = Objects.requireNonNull(availability, "availability");
    this.blocksOthers = blocksOthers;
    this.parts = List.copyOf(parts);
    List<UsePeriod> sorted = new ArrayList<>(periods);
    sorted.sort(UsePeriod.ORDER);
    this.periods = List.copyOf(sorted);
    this.weekdays = weekdays;
    this.price = Objects.requireNonNull(price, "price");
    this.rules = Objects.requireNonNull(rules, "rules");
    this.archivedAt = archivedAt;
    this.version = version;
    this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
  }

  /** Uso novo da instalación. As partes non poden traer id. */
  public static FacilityUse create(UseId id, FacilityId facilityId, UseSpec spec, Instant now) {
    Checked c = check(spec, List.of());
    return new FacilityUse(
        id,
        facilityId,
        c.name(),
        spec.rentalMode(),
        c.capacity(),
        spec.slotMinutes(),
        spec.availability(),
        spec.blocksOthers(),
        c.parts(),
        spec.periods(),
        spec.weekdays(),
        spec.price(),
        spec.rules(),
        null,
        null,
        now,
        now);
  }

  /**
   * Uso dun ficheiro furar-export (ADR-0018): as mesmas regras ca un novo, pero co seu id, os ids
   * das súas partes, as súas datas e, se o ten, cando se arquivou.
   */
  public static FacilityUse restore(
      UseId id,
      FacilityId facilityId,
      UseSpec spec,
      @Nullable Instant archivedAt,
      Instant createdAt,
      Instant updatedAt) {
    List<UsePart> known = new ArrayList<>();
    for (UseSpec.PartSpec part : spec.parts()) {
      if (part != null && part.id() != null) {
        known.add(new UsePart(part.id(), part.name() == null ? "" : part.name()));
      }
    }
    Checked c = check(spec, known);
    return new FacilityUse(
        id,
        facilityId,
        c.name(),
        spec.rentalMode(),
        c.capacity(),
        spec.slotMinutes(),
        spec.availability(),
        spec.blocksOthers(),
        c.parts(),
        spec.periods(),
        spec.weekdays(),
        spec.price(),
        spec.rules(),
        archivedAt,
        null,
        createdAt,
        updatedAt);
  }

  /** Fila gardada, sen comprobar de novo (xa a comprobou o dominio e a base). */
  public static FacilityUse rehydrate(
      UseId id,
      FacilityId facilityId,
      String name,
      RentalMode rentalMode,
      @Nullable Integer capacity,
      int slotMinutes,
      UseAvailability availability,
      boolean blocksOthers,
      List<UsePart> parts,
      List<UsePeriod> periods,
      int weekdays,
      UsePrice price,
      UseRules rules,
      @Nullable Instant archivedAt,
      @Nullable Long version,
      Instant createdAt,
      Instant updatedAt) {
    return new FacilityUse(
        id,
        facilityId,
        name,
        rentalMode,
        capacity,
        slotMinutes,
        availability,
        blocksOthers,
        parts,
        periods,
        weekdays,
        price,
        rules,
        archivedAt,
        version,
        createdAt,
        updatedAt);
  }

  /**
   * Edición enteira do uso: mesma identidade e versión (o adaptador compróbaa con {@code
   * If-Match}). As partes con id consérvanse (cambian de nome ou de posición); as que non veñen
   * arquívanse; as novas reciben id.
   */
  public FacilityUse update(UseSpec spec, Instant now) {
    if (archivedAt != null) {
      throw new DomainException(UseError.ARCHIVED, Map.of("name", name));
    }
    Checked c = check(spec, parts);
    return new FacilityUse(
        id,
        facilityId,
        c.name(),
        spec.rentalMode(),
        c.capacity(),
        spec.slotMinutes(),
        spec.availability(),
        spec.blocksOthers(),
        c.parts(),
        spec.periods(),
        spec.weekdays(),
        spec.price(),
        spec.rules(),
        null,
        version,
        createdAt,
        now);
  }

  /** Arquiva o uso: deixa de reservarse e de contar para o borrador; as filas quedan. */
  public FacilityUse archive(Instant now) {
    if (archivedAt != null) {
      return this;
    }
    return new FacilityUse(
        id,
        facilityId,
        name,
        rentalMode,
        capacity,
        slotMinutes,
        availability,
        blocksOthers,
        parts,
        periods,
        weekdays,
        price,
        rules,
        now,
        version,
        createdAt,
        now);
  }

  /**
   * Se o cambio desde {@code previous} pode deixar mal reservas futuras (P1): outro modo, outra
   * duración, menos prazas ou unha parte quitada. Nome e datas pódense cambiar sempre.
   */
  public boolean changesBookingsOf(FacilityUse previous) {
    return rentalMode != previous.rentalMode
        || slotMinutes != previous.slotMinutes
        || (rentalMode == RentalMode.SEATS
            && previous.capacity != null
            && capacity != null
            && capacity < previous.capacity)
        || !removedParts(previous).isEmpty();
  }

  /** Partes de {@code previous} que xa non están (arquívanse). */
  public List<UsePart> removedParts(FacilityUse previous) {
    Set<UUID> kept = new HashSet<>();
    parts.forEach(p -> kept.add(p.id()));
    return previous.parts.stream().filter(p -> !kept.contains(p.id())).toList();
  }

  /**
   * Os días en que este uso pasa a bloquear os demais respecto de {@code previous} (nulo = uso
   * novo): neles hai que preguntar polas reservas afectadas doutros usos.
   */
  public List<UsePeriod> newlyBlockedSince(@Nullable FacilityUse previous) {
    if (!blocksOthers || archivedAt != null) {
      return List.of();
    }
    boolean blockedBefore =
        previous != null
            && previous.blocksOthers
            && previous.archivedAt == null
            && (weekdays & ~previous.weekdays) == 0;
    // Se gaña días da semana, todas as súas datas poden ter días novos: pregúntase por todas
    // (os días que xa bloqueaba non teñen reservas doutros usos).
    return UsePeriod.uncovered(periods, blockedBefore ? previous.periods : List.of());
  }

  /** Se os dous bloquean os demais e algunha das súas datas se pisan (P2). */
  public boolean exclusiveOverlapWith(FacilityUse other) {
    if (!blocksOthers
        || !other.blocksOthers
        || other.id.equals(id)
        || archivedAt != null
        || other.archivedAt != null) {
      return false;
    }
    int shared = weekdays & other.weekdays;
    if (shared == 0) {
      return false;
    }
    for (UsePeriod p : periods) {
      for (UsePeriod q : other.periods) {
        if (p.overlaps(q)) {
          LocalDate from = p.from().isAfter(q.from()) ? p.from() : q.from();
          LocalDate to = p.to().isBefore(q.to()) ? p.to() : q.to();
          if (Weekdays.anyBetween(shared, from, to)) {
            return true;
          }
        }
      }
    }
    return false;
  }

  /**
   * Se o uso vale ese día: vivo, nas súas datas (se é «só unhas datas») e nun dos seus días da
   * semana. Non mira o horario nin os usos que bloquean.
   */
  public boolean availableOn(LocalDate date) {
    if (archivedAt != null || !Weekdays.includes(weekdays, date)) {
      return false;
    }
    return availability == UseAvailability.ALWAYS
        || periods.stream().anyMatch(p -> !date.isBefore(p.from()) && !date.isAfter(p.to()));
  }

  private record Checked(String name, @Nullable Integer capacity, List<UsePart> parts) {}

  /** Todas as regras do uso; os erros por campo saen xuntos en {@code facility.use-invalid}. */
  private static Checked check(UseSpec spec, List<UsePart> current) {
    Objects.requireNonNull(spec.rentalMode(), "rentalMode");
    Objects.requireNonNull(spec.availability(), "availability");
    if (spec.blocksOthers() && spec.availability() != UseAvailability.DATES) {
      throw new DomainException(
          UseError.BLOCKS_OTHERS_NEEDS_DATES,
          Map.of(),
          List.of(FieldViolation.of("blocksOthers", UseError.BLOCKS_OTHERS_NEEDS_DATES)));
    }
    List<FieldViolation> invalid = new ArrayList<>();
    String name = spec.name() == null ? "" : spec.name().strip();
    if (name.isEmpty() || name.length() > NAME_MAX) {
      invalid.add(size("name", NAME_MAX));
    }
    if (!SLOT_MINUTES.contains(spec.slotMinutes())) {
      invalid.add(
          FieldViolation.of(
              "slotMinutes", ValidationError.INVALID, Map.of("allowed", "30, 60, 90, 120")));
    }
    Integer capacity = spec.capacity();
    if (spec.rentalMode() == RentalMode.SEATS) {
      if (capacity != null && (capacity < CAPACITY_MIN || capacity > CAPACITY_MAX)) {
        invalid.add(
            FieldViolation.of(
                "capacity",
                ValidationError.RANGE,
                Map.of("min", CAPACITY_MIN, "max", CAPACITY_MAX)));
      }
    } else if (capacity != null) {
      invalid.add(FieldViolation.of("capacity", ValidationError.INVALID));
    }
    if (spec.rentalMode() != RentalMode.PARTS && !spec.parts().isEmpty()) {
      invalid.add(FieldViolation.of("parts", ValidationError.INVALID));
    }
    List<UsePart> parts = parts(spec, current, invalid);
    if (spec.availability() == UseAvailability.ALWAYS && !spec.periods().isEmpty()) {
      invalid.add(FieldViolation.of("periods", ValidationError.INVALID));
    }
    if (spec.periods().size() > PERIODS_MAX) {
      invalid.add(
          FieldViolation.of("periods", ValidationError.SIZE, Map.of("min", 1, "max", PERIODS_MAX)));
    }
    for (int i = 0; i < spec.periods().size(); i++) {
      UsePeriod period = spec.periods().get(i);
      if (period.to().isBefore(period.from())) {
        invalid.add(FieldViolation.of("periods[" + i + "].to", ValidationError.RANGE));
      }
    }
    if (!invalid.isEmpty()) {
      throw new DomainException(UseError.INVALID, Map.of(), invalid);
    }
    if (spec.rentalMode() == RentalMode.PARTS && parts.size() < PARTS_MIN) {
      throw new DomainException(
          UseError.PARTS_REQUIRED,
          Map.of("min", PARTS_MIN),
          List.of(FieldViolation.of("parts", UseError.PARTS_REQUIRED, Map.of("min", PARTS_MIN))));
    }
    if (spec.rentalMode() == RentalMode.SEATS && capacity == null) {
      throw new DomainException(
          UseError.CAPACITY_REQUIRED,
          Map.of(),
          List.of(FieldViolation.of("capacity", UseError.CAPACITY_REQUIRED)));
    }
    if (spec.availability() == UseAvailability.DATES && spec.periods().isEmpty()) {
      throw new DomainException(
          UseError.DATES_REQUIRED,
          Map.of(),
          List.of(FieldViolation.of("periods", UseError.DATES_REQUIRED)));
    }
    checkNoOverlap(spec.periods());
    if (!Weekdays.valid(spec.weekdays())) {
      throw new DomainException(
          UseError.WEEKDAYS_INVALID,
          Map.of(),
          List.of(FieldViolation.of("weekdays", UseError.WEEKDAYS_INVALID)));
    }
    List<FieldViolation> price = spec.price().violations();
    if (!price.isEmpty()) {
      throw new DomainException(UseError.PRICE_INVALID, Map.of("max", UsePrice.AMOUNT_MAX), price);
    }
    List<FieldViolation> rules = spec.rules().violations();
    if (!rules.isEmpty()) {
      throw new DomainException(UseError.RULE_INVALID, Map.of(), rules);
    }
    return new Checked(name, spec.rentalMode() == RentalMode.SEATS ? capacity : null, parts);
  }

  /** As partes na orde pedida: as coñecidas conservan o id; as novas reciben un. */
  private static List<UsePart> parts(
      UseSpec spec, List<UsePart> current, List<FieldViolation> invalid) {
    if (spec.rentalMode() != RentalMode.PARTS) {
      return List.of();
    }
    if (spec.parts().size() > PARTS_MAX) {
      invalid.add(
          FieldViolation.of(
              "parts", ValidationError.SIZE, Map.of("min", PARTS_MIN, "max", PARTS_MAX)));
      return List.of();
    }
    Set<UUID> known = new HashSet<>();
    current.forEach(p -> known.add(p.id()));
    Set<UUID> seen = new HashSet<>();
    List<UsePart> parts = new ArrayList<>();
    for (int i = 0; i < spec.parts().size(); i++) {
      UseSpec.PartSpec part = spec.parts().get(i);
      String partName = part.name() == null ? "" : part.name().strip();
      if (partName.isEmpty() || partName.length() > NAME_MAX) {
        invalid.add(size("parts[" + i + "].name", NAME_MAX));
      }
      UUID partId = part.id();
      if (partId == null) {
        partId = Ids.newV7();
      } else if (!known.contains(partId) || !seen.add(partId)) {
        invalid.add(FieldViolation.of("parts[" + i + "].id", ValidationError.INVALID));
      }
      parts.add(new UsePart(partId, partName));
    }
    return parts;
  }

  private static void checkNoOverlap(List<UsePeriod> periods) {
    for (int i = 0; i < periods.size(); i++) {
      for (int j = i + 1; j < periods.size(); j++) {
        if (periods.get(i).overlaps(periods.get(j))) {
          throw new DomainException(
              UseError.PERIOD_OVERLAP,
              Map.of(),
              List.of(FieldViolation.of("periods[" + j + "]", UseError.PERIOD_OVERLAP)));
        }
      }
    }
  }

  private static FieldViolation size(String field, int max) {
    return FieldViolation.of(field, ValidationError.SIZE, Map.of("min", 1, "max", max));
  }

  public UseId id() {
    return id;
  }

  public FacilityId facilityId() {
    return facilityId;
  }

  public String name() {
    return name;
  }

  public RentalMode rentalMode() {
    return rentalMode;
  }

  public @Nullable Integer capacity() {
    return capacity;
  }

  public int slotMinutes() {
    return slotMinutes;
  }

  public UseAvailability availability() {
    return availability;
  }

  public boolean blocksOthers() {
    return blocksOthers;
  }

  public List<UsePart> parts() {
    return parts;
  }

  public List<UsePeriod> periods() {
    return periods;
  }

  /** Máscara de días da semana ({@link Weekdays}). */
  public int weekdays() {
    return weekdays;
  }

  public UsePrice price() {
    return price;
  }

  public UseRules rules() {
    return rules;
  }

  public @Nullable Instant archivedAt() {
    return archivedAt;
  }

  public boolean isArchived() {
    return archivedAt != null;
  }

  public @Nullable Long version() {
    return version;
  }

  public Instant createdAt() {
    return createdAt;
  }

  public Instant updatedAt() {
    return updatedAt;
  }
}
