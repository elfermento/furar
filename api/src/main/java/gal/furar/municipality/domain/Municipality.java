// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.municipality.domain;

import gal.furar.shared.domain.DomainException;
import gal.furar.shared.domain.Language;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Concello (agregado). O slug non cambia nunca; o resto edítase cunha versión esperada (ADR-0010).
 */
public final class Municipality {

  public static final int NAME_MAX = 100;

  private final MunicipalityId id;
  private final Slug slug;
  private final String name;
  private final List<Language> languages;
  private final Language defaultLanguage;
  private final Theme theme;
  private final LegalDetails legal;
  private final LegalTexts legalTexts;
  private final SportsContact sportsContact;
  private final PaymentNotes paymentNotes;
  private final @Nullable Long version;
  private final Instant createdAt;
  private final Instant updatedAt;

  private Municipality(
      MunicipalityId id,
      Slug slug,
      String name,
      List<Language> languages,
      Language defaultLanguage,
      Theme theme,
      LegalDetails legal,
      LegalTexts legalTexts,
      SportsContact sportsContact,
      PaymentNotes paymentNotes,
      @Nullable Long version,
      Instant createdAt,
      Instant updatedAt) {
    this.id = Objects.requireNonNull(id, "id");
    this.slug = Objects.requireNonNull(slug, "slug");
    this.name = checkName(name);
    this.languages = checkLanguages(languages, defaultLanguage);
    this.defaultLanguage = defaultLanguage;
    this.theme = Objects.requireNonNull(theme, "theme");
    this.legal = Objects.requireNonNull(legal, "legal");
    this.legalTexts = Objects.requireNonNull(legalTexts, "legalTexts");
    this.sportsContact = Objects.requireNonNull(sportsContact, "sportsContact");
    this.paymentNotes = Objects.requireNonNull(paymentNotes, "paymentNotes");
    this.version = version;
    this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
  }

  /**
   * Alta. Os campos personalizados do concello non son parte do agregado: gárdaos o almacén de
   * {@code customfields} (ADR-0011), co caso de uso como dono da transacción.
   */
  public static Municipality create(
      MunicipalityId id,
      Slug slug,
      String name,
      List<Language> languages,
      Language defaultLanguage,
      Theme theme,
      Instant now) {
    return new Municipality(
        id,
        slug,
        name,
        languages,
        defaultLanguage,
        theme,
        LegalDetails.EMPTY,
        LegalTexts.EMPTY,
        SportsContact.EMPTY,
        PaymentNotes.DEFAULTS,
        null,
        now,
        now);
  }

  /**
   * Concello que chega dun ficheiro {@code furar-export} (ADR-0018): conserva id e datas. Os campos
   * personalizados cárganse despois das súas definicións.
   */
  public static Municipality restore(
      MunicipalityId id,
      Slug slug,
      String name,
      List<Language> languages,
      Language defaultLanguage,
      Theme theme,
      Instant createdAt,
      Instant updatedAt) {
    return new Municipality(
        id,
        slug,
        name,
        languages,
        defaultLanguage,
        theme,
        LegalDetails.EMPTY,
        LegalTexts.EMPTY,
        SportsContact.EMPTY,
        PaymentNotes.DEFAULTS,
        null,
        createdAt,
        updatedAt);
  }

  /** Datos legais na alta ou na importación, sen tocar {@code updatedAt}. */
  public Municipality withLegal(LegalDetails details) {
    return new Municipality(
        id,
        slug,
        name,
        languages,
        defaultLanguage,
        theme,
        details,
        legalTexts,
        sportsContact,
        paymentNotes,
        version,
        createdAt,
        updatedAt);
  }

  public static Municipality rehydrate(
      MunicipalityId id,
      Slug slug,
      String name,
      List<Language> languages,
      Language defaultLanguage,
      Theme theme,
      LegalDetails legal,
      long version,
      Instant createdAt,
      Instant updatedAt) {
    return rehydrate(
        id,
        slug,
        name,
        languages,
        defaultLanguage,
        theme,
        legal,
        SportsContact.EMPTY,
        PaymentNotes.DEFAULTS,
        version,
        createdAt,
        updatedAt);
  }

  /** Fila gardada co contacto do servizo de deportes e as notas de pago (D2-02). */
  public static Municipality rehydrate(
      MunicipalityId id,
      Slug slug,
      String name,
      List<Language> languages,
      Language defaultLanguage,
      Theme theme,
      LegalDetails legal,
      SportsContact sportsContact,
      PaymentNotes paymentNotes,
      long version,
      Instant createdAt,
      Instant updatedAt) {
    return rehydrate(
        id,
        slug,
        name,
        languages,
        defaultLanguage,
        theme,
        legal,
        LegalTexts.EMPTY,
        sportsContact,
        paymentNotes,
        version,
        createdAt,
        updatedAt);
  }

  /** Fila gardada enteira, cos textos propios das páxinas legais. */
  public static Municipality rehydrate(
      MunicipalityId id,
      Slug slug,
      String name,
      List<Language> languages,
      Language defaultLanguage,
      Theme theme,
      LegalDetails legal,
      LegalTexts legalTexts,
      SportsContact sportsContact,
      PaymentNotes paymentNotes,
      long version,
      Instant createdAt,
      Instant updatedAt) {
    return new Municipality(
        id,
        slug,
        name,
        languages,
        defaultLanguage,
        theme,
        legal,
        legalTexts,
        sportsContact,
        paymentNotes,
        version,
        createdAt,
        updatedAt);
  }

  /** Textos propios das páxinas legais na importación, sen tocar {@code updatedAt}. */
  public Municipality withLegalTexts(LegalTexts texts) {
    return new Municipality(
        id,
        slug,
        name,
        languages,
        defaultLanguage,
        theme,
        legal,
        texts,
        sportsContact,
        paymentNotes,
        version,
        createdAt,
        updatedAt);
  }

  /** Textos propios das páxinas legais (axustes do concello). */
  public Municipality withLegalTexts(LegalTexts texts, Instant now) {
    return new Municipality(
        id,
        slug,
        name,
        languages,
        defaultLanguage,
        theme,
        legal,
        texts,
        sportsContact,
        paymentNotes,
        version,
        createdAt,
        now);
  }

  /** Contacto do servizo de deportes (axustes do concello). */
  public Municipality withSportsContact(SportsContact contact, Instant now) {
    return new Municipality(
        id,
        slug,
        name,
        languages,
        defaultLanguage,
        theme,
        legal,
        legalTexts,
        contact,
        paymentNotes,
        version,
        createdAt,
        now);
  }

  /** Textos de pago e devolución (axustes do concello). */
  public Municipality withPaymentNotes(PaymentNotes notes, Instant now) {
    return new Municipality(
        id,
        slug,
        name,
        languages,
        defaultLanguage,
        theme,
        legal,
        legalTexts,
        sportsContact,
        notes,
        version,
        createdAt,
        now);
  }

  /**
   * Edita nome, idiomas, tema e datos legais. Cambia sempre {@code updatedAt}, así que a versión
   * sobe tamén cando só cambian os campos personalizados.
   */
  public Municipality update(
      String newName,
      List<Language> newLanguages,
      Language newDefault,
      Theme newTheme,
      LegalDetails newLegal,
      Instant now) {
    return new Municipality(
        id,
        slug,
        newName,
        newLanguages,
        newDefault,
        newTheme,
        newLegal,
        legalTexts,
        sportsContact,
        paymentNotes,
        version,
        createdAt,
        now);
  }

  private static String checkName(String name) {
    if (name == null || name.isBlank() || name.strip().length() > NAME_MAX) {
      throw new DomainException(MunicipalityError.NAME_INVALID, Map.of("max", NAME_MAX));
    }
    return name.strip();
  }

  private static List<Language> checkLanguages(List<Language> languages, Language defaultLanguage) {
    if (languages == null
        || languages.isEmpty()
        || defaultLanguage == null
        || new LinkedHashSet<>(languages).size() != languages.size()
        || !languages.contains(defaultLanguage)) {
      throw new DomainException(MunicipalityError.LANGUAGES_INVALID);
    }
    return List.copyOf(languages);
  }

  public MunicipalityId id() {
    return id;
  }

  public Slug slug() {
    return slug;
  }

  public String name() {
    return name;
  }

  public List<Language> languages() {
    return languages;
  }

  public Language defaultLanguage() {
    return defaultLanguage;
  }

  public Theme theme() {
    return theme;
  }

  public LegalDetails legal() {
    return legal;
  }

  public LegalTexts legalTexts() {
    return legalTexts;
  }

  public SportsContact sportsContact() {
    return sportsContact;
  }

  public PaymentNotes paymentNotes() {
    return paymentNotes;
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
