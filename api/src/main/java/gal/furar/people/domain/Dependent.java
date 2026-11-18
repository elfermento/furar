// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.people.domain;

import gal.furar.shared.domain.DomainException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Persoa a cargo (agregado; ADR-0031, punto 1): unha ficha da conta de quen reserva, co nome e
 * apelidos, o ano de nacemento e a data en que declarou «Esta persoa está ao meu cargo». Sen DNI,
 * parentesco nin datos de saúde. Inmutable: as invariantes van no construtor e cada cambio é un
 * método con nome de negocio que devolve unha copia (ADR-0005, A13).
 */
public final class Dependent {

  /** Fichas activas por conta, como moito. */
  public static final int MAX_ACTIVE = 10;

  public static final int NAME_MAX = 200;

  /** O ano de nacemento vai do actual menos este número ao actual. */
  public static final int MAX_AGE = 120;

  private static final ZoneId ZONE = ZoneId.of("Europe/Madrid");

  private final DependentId id;
  private final String accountSubject;
  private final String name;
  private final int birthYear;
  private final Instant declaredAt;
  private final @Nullable Instant deactivatedAt;
  private final @Nullable Long version;
  private final Instant createdAt;
  private final Instant updatedAt;

  private Dependent(
      DependentId id,
      String accountSubject,
      String name,
      int birthYear,
      Instant declaredAt,
      @Nullable Instant deactivatedAt,
      @Nullable Long version,
      Instant createdAt,
      Instant updatedAt) {
    this.id = Objects.requireNonNull(id, "id");
    this.accountSubject = Objects.requireNonNull(accountSubject, "accountSubject");
    this.name = Objects.requireNonNull(name, "name");
    this.birthYear = birthYear;
    this.declaredAt = Objects.requireNonNull(declaredAt, "declaredAt");
    this.deactivatedAt = deactivatedAt;
    this.version = version;
    this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
    if (name.isEmpty() || name.length() > NAME_MAX) {
      throw new IllegalArgumentException("name length out of range");
    }
  }

  /**
   * Ficha nova.
   *
   * @param inMyCare a declaración «Esta persoa está ao meu cargo»: obrigatoria
   * @throws DomainException {@code people.dependent-declaration-required} ou {@code
   *     people.dependent-birth-year-invalid}
   */
  public static Dependent create(
      DependentId id,
      String accountSubject,
      String name,
      int birthYear,
      boolean inMyCare,
      Instant now) {
    check(birthYear, inMyCare, now);
    return new Dependent(id, accountSubject, clean(name), birthYear, now, null, null, now, now);
  }

  public static Dependent rehydrate(
      DependentId id,
      String accountSubject,
      String name,
      int birthYear,
      Instant declaredAt,
      @Nullable Instant deactivatedAt,
      long version,
      Instant createdAt,
      Instant updatedAt) {
    return new Dependent(
        id,
        accountSubject,
        name,
        birthYear,
        declaredAt,
        deactivatedAt,
        version,
        createdAt,
        updatedAt);
  }

  /**
   * Edición: a declaración vólvese facer e gárdase a súa data nova.
   *
   * @throws DomainException {@code people.dependent-not-found} se está desactivada; {@code
   *     people.dependent-declaration-required} ou {@code people.dependent-birth-year-invalid}
   */
  public Dependent update(String newName, int newBirthYear, boolean inMyCare, Instant now) {
    if (deactivatedAt != null) {
      throw new DomainException(PeopleError.DEPENDENT_NOT_FOUND);
    }
    check(newBirthYear, inMyCare, now);
    return new Dependent(
        id, accountSubject, clean(newName), newBirthYear, now, null, version, createdAt, now);
  }

  /** «Eliminada» pola persoa pero con reservas: xa non se lista, non se usa nin se edita. */
  public Dependent deactivate(Instant now) {
    return new Dependent(
        id, accountSubject, name, birthYear, declaredAt, now, version, createdAt, now);
  }

  private static void check(int birthYear, boolean inMyCare, Instant now) {
    if (!inMyCare) {
      throw new DomainException(PeopleError.DEPENDENT_DECLARATION_REQUIRED);
    }
    int thisYear = now.atZone(ZONE).getYear();
    if (birthYear < thisYear - MAX_AGE || birthYear > thisYear) {
      throw new DomainException(PeopleError.DEPENDENT_BIRTH_YEAR_INVALID);
    }
  }

  /** Sen espazos ao redor nin repetidos por dentro: «Ana Rey » e «Ana Rey» son a mesma. */
  private static String clean(String name) {
    return Objects.requireNonNull(name, "name").strip().replaceAll("\\s+", " ");
  }

  public DependentId id() {
    return id;
  }

  public String accountSubject() {
    return accountSubject;
  }

  public String name() {
    return name;
  }

  public int birthYear() {
    return birthYear;
  }

  public Instant declaredAt() {
    return declaredAt;
  }

  public @Nullable Instant deactivatedAt() {
    return deactivatedAt;
  }

  public boolean isActive() {
    return deactivatedAt == null;
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
