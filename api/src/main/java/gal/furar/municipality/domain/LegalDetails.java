// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.municipality.domain;

import gal.furar.shared.domain.DomainException;
import gal.furar.shared.domain.FieldViolation;
import gal.furar.shared.domain.ValidationError;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * Datos legais do concello (ADR-0017 punto 8, AP3-01, AP4-01): todos opcionais. Enchen as páxinas
 * que xera Furar (privacidade, RGPD art. 13.1.b; declaración de accesibilidade, RD 1112/2018 arts.
 * 13 e 16; aviso legal, LSSI art. 10) e din como se publica cada unha ({@link LegalPageSettings}).
 * Sen valor, a páxina mostra o que hai (revisión do 2026-11-03: xa non hai datos do despregue).
 *
 * @param ineCode código INE do municipio (5 díxitos)
 * @param dir3Code código DIR3 da entidade (p. ex. {@code L01150548})
 * @param legalEntityName denominación oficial ({@code Concello de Narón})
 * @param eOfficeUrl sede electrónica (https)
 * @param nif NIF da entidade (aviso legal)
 * @param address domicilio (aviso legal)
 * @param generalEmail correo xeral do concello (aviso legal, LSSI art. 10.1.b)
 * @param dpoEmail correo do DPD do concello
 * @param dpoPhone teléfono do DPD do concello
 * @param processor encargado que aloxa e opera o servizo (RGPD art. 28); baleiro se o aloxa o
 *     propio concello
 * @param accessibilityEmail correo para comunicacións de accesibilidade
 * @param accessibilityComplaintUrl procedemento de reclamación de accesibilidade (https)
 * @param accessibilityUnit unidade responsable de accesibilidade (art. 16 RD 1112/2018)
 * @param privacy modo e páxina propia de «Privacidade»
 * @param accessibility modo e páxina propia de «Accesibilidade»
 * @param legalNotice modo e páxina propia de «Aviso legal»
 */
public record LegalDetails(
    @Nullable String ineCode,
    @Nullable String dir3Code,
    @Nullable String legalEntityName,
    @Nullable String eOfficeUrl,
    @Nullable String nif,
    @Nullable String address,
    @Nullable String generalEmail,
    @Nullable String dpoEmail,
    @Nullable String dpoPhone,
    @Nullable String processor,
    @Nullable String accessibilityEmail,
    @Nullable String accessibilityComplaintUrl,
    @Nullable String accessibilityUnit,
    LegalPageSettings privacy,
    LegalPageSettings accessibility,
    LegalPageSettings legalNotice) {

  public static final LegalDetails EMPTY =
      new LegalDetails(
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          LegalPageSettings.FURAR,
          LegalPageSettings.FURAR,
          LegalPageSettings.FURAR);

  public static final int TEXT_MAX = 200;
  public static final int URL_MAX = 500;

  static final Pattern INE = Pattern.compile("^[0-9]{5}$");
  static final Pattern DIR3 = Pattern.compile("^[A-Z][A-Z0-9]{8}$");
  static final Pattern NIF = Pattern.compile("^[A-Z][0-9]{7}[A-Z0-9]$");
  static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
  static final Pattern PHONE = Pattern.compile("^\\+?[0-9 ]{9,15}$");
  static final Pattern HTTPS = Pattern.compile("^https://[^\\s]+$");

  /** Normaliza (baleiro → {@code null}) e valida; os erros saen por campo ({@code legal.*}). */
  public LegalDetails {
    ineCode = blankToNull(ineCode);
    dir3Code = blankToNull(dir3Code);
    legalEntityName = blankToNull(legalEntityName);
    eOfficeUrl = blankToNull(eOfficeUrl);
    nif = blankToNull(nif);
    address = blankToNull(address);
    generalEmail = blankToNull(generalEmail);
    dpoEmail = blankToNull(dpoEmail);
    dpoPhone = blankToNull(dpoPhone);
    processor = blankToNull(processor);
    accessibilityEmail = blankToNull(accessibilityEmail);
    accessibilityComplaintUrl = blankToNull(accessibilityComplaintUrl);
    accessibilityUnit = blankToNull(accessibilityUnit);
    privacy = Objects.requireNonNullElse(privacy, LegalPageSettings.FURAR);
    accessibility = Objects.requireNonNullElse(accessibility, LegalPageSettings.FURAR);
    legalNotice = Objects.requireNonNullElse(legalNotice, LegalPageSettings.FURAR);
    List<FieldViolation> errors = new ArrayList<>();
    pattern(errors, "ineCode", ineCode, INE);
    pattern(errors, "dir3Code", dir3Code, DIR3);
    text(errors, "legalEntityName", legalEntityName);
    url(errors, "eOfficeUrl", eOfficeUrl);
    pattern(errors, "nif", nif, NIF);
    text(errors, "address", address);
    email(errors, "generalEmail", generalEmail);
    email(errors, "dpoEmail", dpoEmail);
    pattern(errors, "dpoPhone", dpoPhone, PHONE);
    text(errors, "processor", processor);
    page(errors, "privacy", privacy);
    email(errors, "accessibilityEmail", accessibilityEmail);
    url(errors, "accessibilityComplaintUrl", accessibilityComplaintUrl);
    text(errors, "accessibilityUnit", accessibilityUnit);
    page(errors, "accessibility", accessibility);
    page(errors, "legalNotice", legalNotice);
    if (!errors.isEmpty()) {
      throw DomainException.validation(errors);
    }
  }

  private static @Nullable String blankToNull(@Nullable String value) {
    return value == null || value.isBlank() ? null : value.strip();
  }

  private static void pattern(
      List<FieldViolation> errors, String field, @Nullable String value, Pattern pattern) {
    if (value != null && !pattern.matcher(value).matches()) {
      errors.add(FieldViolation.of("legal." + field, ValidationError.PATTERN));
    }
  }

  private static void text(List<FieldViolation> errors, String field, @Nullable String value) {
    if (value != null && value.length() > TEXT_MAX) {
      errors.add(
          FieldViolation.of("legal." + field, ValidationError.SIZE, Map.of("max", TEXT_MAX)));
    }
  }

  private static void email(List<FieldViolation> errors, String field, @Nullable String value) {
    if (value != null && (value.length() > TEXT_MAX || !EMAIL.matcher(value).matches())) {
      errors.add(FieldViolation.of("legal." + field, ValidationError.PATTERN));
    }
  }

  private static void url(List<FieldViolation> errors, String field, @Nullable String value) {
    if (value != null && (value.length() > URL_MAX || !HTTPS.matcher(value).matches())) {
      errors.add(FieldViolation.of("legal." + field, ValidationError.PATTERN));
    }
  }

  /** O enderezo da páxina propia vai en {@code legal.<páxina>Url}, como no DTO. */
  private static void page(List<FieldViolation> errors, String page, LegalPageSettings settings) {
    String field = page + "Url";
    if (settings.url() == null) {
      if (settings.mode() == LegalPageMode.URL) {
        errors.add(FieldViolation.of("legal." + field, ValidationError.REQUIRED));
      }
      return;
    }
    url(errors, field, settings.url());
  }
}
