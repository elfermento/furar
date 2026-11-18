// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.domain;

import gal.furar.shared.domain.DomainException;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Instalación (agregado): o que se reserva (especificación, bloque 1; ADR-0022). Inmutable: as
 * invariantes van no construtor e cada cambio é un método con nome de negocio que devolve unha
 * copia (ADR-0005, A13). O nome é un só texto, como o escribe o concello; a unicidade sen
 * maiúsculas garántea a base. Cada sección da ficha (nome, datos, descrición, enderezo, servizos,
 * estado) cámbiase cun método propio e garda co {@code If-Match} da instalación.
 */
public final class Facility {

  public static final int NAME_MAX = 100;

  private final FacilityId id;
  private final String name;
  private final FacilityStatus status;
  private final @Nullable Address address;
  private final boolean published;
  private final FacilityDetails details;
  private final @Nullable FacilityDescription description;
  private final @Nullable HouseRules houseRules;
  private final FacilityPaymentNotes paymentNotes;
  private final Set<Amenity> amenities;
  private final @Nullable Long version;
  private final Instant createdAt;
  private final Instant updatedAt;

  private Facility(
      FacilityId id,
      String name,
      FacilityStatus status,
      @Nullable Address address,
      boolean published,
      FacilityDetails details,
      @Nullable FacilityDescription description,
      @Nullable HouseRules houseRules,
      FacilityPaymentNotes paymentNotes,
      Collection<Amenity> amenities,
      @Nullable Long version,
      Instant createdAt,
      Instant updatedAt) {
    this.id = Objects.requireNonNull(id, "id");
    this.name = checkName(name);
    this.status = Objects.requireNonNull(status, "status");
    if (published && address == null) {
      throw new DomainException(FacilityError.NOT_PUBLISHABLE);
    }
    this.address = address;
    this.published = published;
    this.details = Objects.requireNonNull(details, "details");
    this.description = description;
    this.houseRules = houseRules;
    this.paymentNotes = Objects.requireNonNull(paymentNotes, "paymentNotes");
    EnumSet<Amenity> ordered = EnumSet.noneOf(Amenity.class);
    ordered.addAll(amenities);
    this.amenities = Collections.unmodifiableSet(ordered);
    this.version = version;
    this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
  }

  /**
   * Alta (asistente «Nova instalación»): co nome abonda, o enderezo pode chegar despois. Nace
   * activa e sen publicar: ninguén a ve ata o «Publicar» ({@link #publish}).
   */
  public static Facility create(
      FacilityId id, String name, @Nullable Address address, Instant now) {
    return new Facility(
        id,
        name,
        FacilityStatus.ACTIVE,
        address,
        false,
        FacilityDetails.EMPTY,
        null,
        null,
        FacilityPaymentNotes.NONE,
        Set.of(),
        null,
        now,
        now);
  }

  /** Fila dun ficheiro {@code furar-export} (ADR-0018): conserva id, estado, datos e datas. */
  public static Facility restore(
      FacilityId id,
      String name,
      FacilityStatus status,
      @Nullable Address address,
      boolean published,
      FacilityDetails details,
      @Nullable FacilityDescription description,
      @Nullable HouseRules houseRules,
      FacilityPaymentNotes paymentNotes,
      Collection<Amenity> amenities,
      Instant createdAt,
      Instant updatedAt) {
    return new Facility(
        id,
        name,
        status,
        address,
        published,
        details,
        description,
        houseRules,
        paymentNotes,
        amenities,
        null,
        createdAt,
        updatedAt);
  }

  public static Facility rehydrate(
      FacilityId id,
      String name,
      FacilityStatus status,
      @Nullable Address address,
      boolean published,
      FacilityDetails details,
      @Nullable FacilityDescription description,
      @Nullable HouseRules houseRules,
      FacilityPaymentNotes paymentNotes,
      Collection<Amenity> amenities,
      long version,
      Instant createdAt,
      Instant updatedAt) {
    return new Facility(
        id,
        name,
        status,
        address,
        published,
        details,
        description,
        houseRules,
        paymentNotes,
        amenities,
        version,
        createdAt,
        updatedAt);
  }

  /** Cambia o nome; mesma identidade e versión (o adaptador compróbaa con {@code If-Match}). */
  public Facility rename(String newName, Instant now) {
    return new Facility(
        id,
        newName,
        status,
        address,
        published,
        details,
        description,
        houseRules,
        paymentNotes,
        amenities,
        version,
        createdAt,
        now);
  }

  /** Cambia o estado. As reservas afectadas decídense fóra, antes, baixo o bloqueo. */
  public Facility changeStatus(FacilityStatus newStatus, Instant now) {
    return new Facility(
        id,
        name,
        newStatus,
        address,
        published,
        details,
        description,
        houseRules,
        paymentNotes,
        amenities,
        version,
        createdAt,
        now);
  }

  /** Sección Datos: substitúe a tarxeta enteira (deportes incluídos). */
  public Facility updateDetails(FacilityDetails newDetails, Instant now) {
    return new Facility(
        id,
        name,
        status,
        address,
        published,
        newDetails,
        description,
        houseRules,
        paymentNotes,
        amenities,
        version,
        createdAt,
        now);
  }

  /** Sección Descrición; nula = sen descrición. */
  public Facility describe(@Nullable FacilityDescription newDescription, Instant now) {
    return new Facility(
        id,
        name,
        status,
        address,
        published,
        details,
        newDescription,
        houseRules,
        paymentNotes,
        amenities,
        version,
        createdAt,
        now);
  }

  /** Normas da instalación (texto libre gl/es); nulas = sen normas. */
  public Facility withHouseRules(@Nullable HouseRules newHouseRules, Instant now) {
    return new Facility(
        id,
        name,
        status,
        address,
        published,
        details,
        description,
        newHouseRules,
        paymentNotes,
        amenities,
        version,
        createdAt,
        now);
  }

  /** Pago e devolución desta instalación (só se usan co ámbito «Distinto por instalación»). */
  public Facility withPaymentNotes(FacilityPaymentNotes newPaymentNotes, Instant now) {
    return new Facility(
        id,
        name,
        status,
        address,
        published,
        details,
        description,
        houseRules,
        newPaymentNotes,
        amenities,
        version,
        createdAt,
        now);
  }

  /** Sección Localización: enderezo e punto no mapa. */
  public Facility relocate(Address newAddress, Instant now) {
    return new Facility(
        id,
        name,
        status,
        newAddress,
        published,
        details,
        description,
        houseRules,
        paymentNotes,
        amenities,
        version,
        createdAt,
        now);
  }

  /** Sección Servizos e accesibilidade: a lista enteira. */
  public Facility equip(Collection<Amenity> newAmenities, Instant now) {
    return new Facility(
        id,
        name,
        status,
        address,
        published,
        details,
        description,
        houseRules,
        paymentNotes,
        newAmenities,
        version,
        createdAt,
        now);
  }

  /**
   * «Publicar» (asistente): desde agora a veciñanza a ve, se non lle falta nada (deportes, usos e
   * horario compróbaos o servizo, que viven noutras táboas). Sen enderezo non se publica. Publicar
   * unha xa publicada non cambia nada.
   */
  public Facility publish(Instant now) {
    if (published) {
      return this;
    }
    if (address == null) {
      throw new DomainException(FacilityError.NOT_PUBLISHABLE);
    }
    return new Facility(
        id,
        name,
        status,
        address,
        true,
        details,
        description,
        houseRules,
        paymentNotes,
        amenities,
        version,
        createdAt,
        now);
  }

  /**
   * Cambio nunha sección que vive noutras táboas (fotos): só sobe a versión, para que o {@code
   * If-Match} da instalación tamén a protexa.
   */
  public Facility touch(Instant now) {
    return new Facility(
        id,
        name,
        status,
        address,
        published,
        details,
        description,
        houseRules,
        paymentNotes,
        amenities,
        version,
        createdAt,
        now);
  }

  private static String checkName(String name) {
    if (name == null || name.isBlank() || name.strip().length() > NAME_MAX) {
      throw new DomainException(FacilityError.NAME_INVALID, Map.of("max", NAME_MAX));
    }
    return name.strip();
  }

  public FacilityId id() {
    return id;
  }

  public String name() {
    return name;
  }

  public FacilityStatus status() {
    return status;
  }

  /** Nulo mentres o borrador non o ten. */
  public @Nullable Address address() {
    return address;
  }

  /** Se a publicou o «Publicar» (as anteriores ao asistente, todas). */
  public boolean published() {
    return published;
  }

  public FacilityDetails details() {
    return details;
  }

  public @Nullable FacilityDescription description() {
    return description;
  }

  public @Nullable HouseRules houseRules() {
    return houseRules;
  }

  public FacilityPaymentNotes paymentNotes() {
    return paymentNotes;
  }

  /** Na orde do {@code enum}. */
  public Set<Amenity> amenities() {
    return amenities;
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
