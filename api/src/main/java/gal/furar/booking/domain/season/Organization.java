// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.domain.season;

import gal.furar.booking.domain.BookingError;
import gal.furar.shared.domain.DomainException;
import gal.furar.shared.domain.EmailKey;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * Unha entidade do concello (club, escola, asociación; ADR-0039, punto 7): nome, contacto, correo
 * (obrigatorio mentres está dada de alta), teléfono e os correos das persoas responsables, como
 * moito {@value #MAX_MANAGERS}. Con historial non se borra: arquívase e só queda o nome. Inmutable.
 */
public final class Organization {

  /** Responsables como moito por entidade. */
  public static final int MAX_MANAGERS = 10;

  /** Solicitudes de tempada pendentes como moito por entidade. */
  public static final int MAX_PENDING_REQUESTS = 5;

  private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+$");
  private static final Pattern PHONE = Pattern.compile("^[0-9+() .-]+$");

  private final UUID id;
  private final String name;
  private final @Nullable String contactName;
  private final @Nullable String email;
  private final @Nullable String phone;
  private final List<Manager> managers;
  private final @Nullable Instant archivedAt;
  private final @Nullable Long version;
  private final Instant createdAt;
  private final Instant updatedAt;

  private Organization(
      UUID id,
      String name,
      @Nullable String contactName,
      @Nullable String email,
      @Nullable String phone,
      List<Manager> managers,
      @Nullable Instant archivedAt,
      @Nullable Long version,
      Instant createdAt,
      Instant updatedAt) {
    this.id = Objects.requireNonNull(id, "id");
    this.name = Objects.requireNonNull(name, "name");
    this.contactName = contactName;
    this.email = email;
    this.phone = phone;
    this.managers = List.copyOf(managers);
    this.archivedAt = archivedAt;
    this.version = version;
    this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
    if (archivedAt == null && email == null) {
      throw new IllegalStateException("An active organization has an email");
    }
    if (archivedAt != null && (email != null || contactName != null || phone != null)) {
      throw new IllegalStateException("An archived organization keeps only its name");
    }
  }

  /** Un correo dunha persoa responsable, quen o engadiu e cando. */
  public record Manager(String emailKey, String email, Instant addedAt, String addedBy) {

    public Manager {
      Objects.requireNonNull(emailKey, "emailKey");
      Objects.requireNonNull(email, "email");
      Objects.requireNonNull(addedAt, "addedAt");
      Objects.requireNonNull(addedBy, "addedBy");
    }
  }

  /**
   * Entidade nova, dada de alta.
   *
   * @throws DomainException {@code booking.organization-managers-limit} con máis de {@value
   *     #MAX_MANAGERS} responsables
   * @throws IllegalArgumentException se un dato non ten a forma (a web xa o valida)
   */
  public static Organization create(
      UUID id,
      String name,
      @Nullable String contactName,
      String email,
      @Nullable String phone,
      List<String> managerEmails,
      String actor,
      Instant now) {
    return new Organization(
        id,
        name(name),
        optional(contactName, 120),
        email(Objects.requireNonNull(email, "email")),
        phone(phone),
        managers(List.of(), managerEmails, actor, now),
        null,
        null,
        now,
        now);
  }

  /** Desde a base. */
  public static Organization rehydrate(
      UUID id,
      String name,
      @Nullable String contactName,
      @Nullable String email,
      @Nullable String phone,
      List<Manager> managers,
      @Nullable Instant archivedAt,
      long version,
      Instant createdAt,
      Instant updatedAt) {
    return new Organization(
        id, name, contactName, email, phone, managers, archivedAt, version, createdAt, updatedAt);
  }

  /**
   * Os datos novos: a lista de responsables substitúe a de antes; os que seguen conservan quen os
   * engadiu e cando.
   *
   * @throws DomainException {@code booking.organization-not-found} se está arquivada; {@code
   *     booking.organization-managers-limit}
   */
  public Organization update(
      String newName,
      @Nullable String newContact,
      String newEmail,
      @Nullable String newPhone,
      List<String> managerEmails,
      String actor,
      Instant now) {
    requireActive();
    return new Organization(
        id,
        name(newName),
        optional(newContact, 120),
        email(Objects.requireNonNull(newEmail, "email")),
        phone(newPhone),
        managers(managers, managerEmails, actor, now),
        null,
        version,
        createdAt,
        now);
  }

  /**
   * Arquivada (ADR-0039, punto 7): queda o nome; sen contacto, correo, teléfono nin responsables.
   */
  public Organization archive(Instant now) {
    requireActive();
    return new Organization(id, name, null, null, null, List.of(), now, version, createdAt, now);
  }

  /**
   * @throws DomainException {@code booking.organization-not-found} se está arquivada
   */
  public void requireActive() {
    if (archivedAt != null) {
      throw new DomainException(BookingError.ORGANIZATION_NOT_FOUND);
    }
  }

  public boolean isArchived() {
    return archivedAt != null;
  }

  /** Se ese correo (xa normalizado con {@link EmailKey}) é dunha persoa responsable. */
  public boolean isManager(String emailKey) {
    return managers.stream().anyMatch(m -> m.emailKey().equals(emailKey));
  }

  private static String name(String value) {
    String stripped = Objects.requireNonNull(value, "name").strip();
    if (stripped.isEmpty() || stripped.length() > 120) {
      throw new IllegalArgumentException("Organization name length");
    }
    return stripped;
  }

  private static @Nullable String optional(@Nullable String value, int max) {
    if (value == null || value.isBlank()) {
      return null;
    }
    String stripped = value.strip();
    if (stripped.length() > max) {
      throw new IllegalArgumentException("Length over " + max);
    }
    return stripped;
  }

  private static String email(String value) {
    String stripped = value.strip();
    if (stripped.length() < 3 || stripped.length() > 254 || !EMAIL.matcher(stripped).matches()) {
      throw new IllegalArgumentException("Invalid email");
    }
    return stripped;
  }

  private static @Nullable String phone(@Nullable String value) {
    String stripped = optional(value, 30);
    if (stripped != null && (stripped.length() < 3 || !PHONE.matcher(stripped).matches())) {
      throw new IllegalArgumentException("Invalid phone");
    }
    return stripped;
  }

  /** Normalizados e sen repetir; os que xa estaban conservan quen e cando. */
  private static List<Manager> managers(
      List<Manager> before, List<String> emails, String actor, Instant now) {
    Map<String, Manager> previous = new LinkedHashMap<>();
    before.forEach(m -> previous.put(m.emailKey(), m));
    Map<String, Manager> result = new LinkedHashMap<>();
    for (String raw : emails) {
      String email = email(Objects.requireNonNull(raw, "manager"));
      String key = EmailKey.of(email);
      if (!result.containsKey(key)) {
        Manager kept = previous.get(key);
        result.put(key, kept != null ? kept : new Manager(key, email, now, actor));
      }
    }
    if (result.size() > MAX_MANAGERS) {
      throw new DomainException(
          BookingError.ORGANIZATION_MANAGERS_LIMIT, Map.of("max", MAX_MANAGERS));
    }
    return new ArrayList<>(result.values());
  }

  public UUID id() {
    return id;
  }

  public String name() {
    return name;
  }

  public @Nullable String contactName() {
    return contactName;
  }

  public @Nullable String email() {
    return email;
  }

  public @Nullable String phone() {
    return phone;
  }

  public List<Manager> managers() {
    return managers;
  }

  public @Nullable Instant archivedAt() {
    return archivedAt;
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
