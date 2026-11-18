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
  private final NoShowPolicy noShowPolicy;
  private final @Nullable Instant openDataSince;
  private final OpenMatchPolicy openMatchPolicy;
  private final boolean installHint;
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
      NoShowPolicy noShowPolicy,
      @Nullable Instant openDataSince,
      OpenMatchPolicy openMatchPolicy,
      boolean installHint,
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
    this.noShowPolicy = Objects.requireNonNull(noShowPolicy, "noShowPolicy");
    this.openDataSince = openDataSince;
    this.openMatchPolicy = Objects.requireNonNull(openMatchPolicy, "openMatchPolicy");
    this.installHint = installHint;
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
        NoShowPolicy.OFF,
        now,
        OpenMatchPolicy.OFF,
        true,
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
        NoShowPolicy.OFF,
        null,
        OpenMatchPolicy.OFF,
        true,
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
        noShowPolicy,
        openDataSince,
        openMatchPolicy,
        installHint,
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

  /** Fila gardada co contacto do servizo de deportes e as notas de pago. */
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
        NoShowPolicy.OFF,
        null,
        OpenMatchPolicy.OFF,
        true,
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
        noShowPolicy,
        openDataSince,
        openMatchPolicy,
        installHint,
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
        noShowPolicy,
        openDataSince,
        openMatchPolicy,
        installHint,
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
        noShowPolicy,
        openDataSince,
        openMatchPolicy,
        installHint,
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
        noShowPolicy,
        openDataSince,
        openMatchPolicy,
        installHint,
        version,
        createdAt,
        now);
  }

  /** Penalización das faltas tal como se gardou ou se importou, sen tocar {@code updatedAt}. */
  public Municipality withNoShowPolicy(NoShowPolicy policy) {
    return withNoShowPolicy(policy, updatedAt);
  }

  /** Penalización das faltas (tarxeta «Non presentados», ADR-0032). */
  public Municipality withNoShowPolicy(NoShowPolicy policy, Instant now) {
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
        policy,
        openDataSince,
        openMatchPolicy,
        installHint,
        version,
        createdAt,
        now);
  }

  /**
   * Datos abertos tal como se gardaron ou se importaron, sen tocar {@code updatedAt} (ADR-0034,
   * punto 2).
   *
   * @param since desde cando publica; nulo = apagado
   */
  public Municipality withOpenDataSince(@Nullable Instant since) {
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
        noShowPolicy,
        since,
        openMatchPolicy,
        installHint,
        version,
        createdAt,
        updatedAt);
  }

  /**
   * Acende ou apaga os datos abertos (Concello › Xeral, ADR-0034). Acender un apagado pon a data
   * nova; acender un aceso consérvaa.
   */
  public Municipality withOpenData(boolean enabled, Instant now) {
    Instant since = enabled ? (openDataSince == null ? now : openDataSince) : null;
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
        noShowPolicy,
        since,
        openMatchPolicy,
        installHint,
        version,
        createdAt,
        now);
  }

  /**
   * «Buscar xente para xogar» tal como se gardou ou se importou, sen tocar {@code updatedAt}
   * (ADR-0040).
   */
  public Municipality withOpenMatchPolicy(OpenMatchPolicy policy) {
    return withOpenMatchPolicy(policy, updatedAt);
  }

  /** «Buscar xente para xogar» e as súas horas (Concello › Xeral, ADR-0040). */
  public Municipality withOpenMatchPolicy(OpenMatchPolicy policy, Instant now) {
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
        noShowPolicy,
        openDataSince,
        Objects.requireNonNull(policy, "policy"),
        installHint,
        version,
        createdAt,
        now);
  }

  /**
   * Aviso para instalar no móbil tal como se gardou ou se importou, sen tocar {@code updatedAt}.
   */
  public Municipality withInstallHint(boolean enabled) {
    return withInstallHint(enabled, updatedAt);
  }

  /** Aviso para instalar no móbil na portada (Concello › Xeral). */
  public Municipality withInstallHint(boolean enabled, Instant now) {
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
        noShowPolicy,
        openDataSince,
        openMatchPolicy,
        enabled,
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
        noShowPolicy,
        openDataSince,
        openMatchPolicy,
        installHint,
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

  public NoShowPolicy noShowPolicy() {
    return noShowPolicy;
  }

  /** Desde cando publica datos abertos; nulo = apagado (ADR-0034). */
  public @Nullable Instant openDataSince() {
    return openDataSince;
  }

  public boolean openData() {
    return openDataSince != null;
  }

  /** «Buscar xente para xogar» (ADR-0040). */
  public OpenMatchPolicy openMatchPolicy() {
    return openMatchPolicy;
  }

  /** Se a portada mostra no móbil o aviso para instalar Furar coma unha app. */
  public boolean installHint() {
    return installHint;
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
