// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.municipality.application.service;

import gal.furar.municipality.application.port.in.LegalData;
import gal.furar.municipality.application.port.in.LegalTextsData;
import gal.furar.municipality.application.port.in.ThemeData;
import gal.furar.municipality.application.port.out.LegalHtmlSanitizer;
import gal.furar.municipality.domain.HexColor;
import gal.furar.municipality.domain.LegalDetails;
import gal.furar.municipality.domain.LegalPageMode;
import gal.furar.municipality.domain.LegalPageSettings;
import gal.furar.municipality.domain.LegalText;
import gal.furar.municipality.domain.LegalTexts;
import gal.furar.municipality.domain.MunicipalityError;
import gal.furar.municipality.domain.Theme;
import gal.furar.shared.domain.DomainException;
import gal.furar.shared.domain.FieldViolation;
import gal.furar.shared.domain.Language;
import gal.furar.shared.domain.LanguagePolicy;
import gal.furar.shared.domain.ValidationError;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * Idiomas, tema, datos legais e textos legais tal como chegan (alta, edición e importación) →
 * dominio.
 */
final class MunicipalityInput {

  private MunicipalityInput() {}

  static List<Language> languages(List<String> codes, LanguagePolicy languagePolicy) {
    if (codes == null) {
      throw new DomainException(MunicipalityError.LANGUAGES_INVALID);
    }
    List<Language> languages =
        codes.stream()
            .map(Language::fromCode)
            .map(l -> l.orElseThrow(() -> new DomainException(MunicipalityError.LANGUAGES_INVALID)))
            .toList();
    languagePolicy
        .firstMissing(languages)
        .ifPresent(
            missing -> {
              throw new DomainException(
                  MunicipalityError.LANGUAGE_REQUIRED, Map.of("language", missing.code()));
            });
    return languages;
  }

  static Language language(String code) {
    return Language.fromCode(code)
        .orElseThrow(() -> new DomainException(MunicipalityError.LANGUAGES_INVALID));
  }

  static LegalDetails legal(@Nullable LegalData l) {
    if (l == null) {
      return LegalDetails.EMPTY;
    }
    String dpoEmail = l.dpoEmail();
    String dpoPhone = l.dpoPhone();
    if (isBlank(dpoEmail) && isBlank(dpoPhone) && !isBlank(l.dpoContact())) {
      // Ficheiro anterior aos campos do DPD: o mesmo que fixo a migración coa táboa.
      dpoEmail = find(LEGACY_EMAIL, l.dpoContact());
      dpoPhone = find(LEGACY_PHONE, l.dpoContact());
    }
    List<FieldViolation> errors = new ArrayList<>();
    LegalPageMode privacy = mode(errors, "privacyMode", l.privacyMode());
    LegalPageMode accessibility = mode(errors, "accessibilityMode", l.accessibilityMode());
    LegalPageMode legalNotice = mode(errors, "legalNoticeMode", l.legalNoticeMode());
    if (!errors.isEmpty()) {
      throw DomainException.validation(errors);
    }
    return new LegalDetails(
        l.ineCode(),
        l.dir3Code(),
        l.legalEntityName(),
        l.eOfficeUrl(),
        l.nif(),
        l.address(),
        l.generalEmail(),
        dpoEmail,
        dpoPhone,
        l.processor(),
        l.accessibilityEmail(),
        l.accessibilityComplaintUrl(),
        l.accessibilityUnit(),
        new LegalPageSettings(privacy, l.privacyUrl()),
        new LegalPageSettings(accessibility, l.accessibilityUrl()),
        new LegalPageSettings(legalNotice, l.legalNoticeUrl()));
  }

  /**
   * Textos propios das páxinas legais: cada idioma pasa pola lista branca antes de chegar ao
   * dominio, que só comproba o tamaño.
   */
  static LegalTexts legalTexts(@Nullable LegalTextsData data, LegalHtmlSanitizer sanitizer) {
    if (data == null) {
      return LegalTexts.EMPTY;
    }
    return new LegalTexts(
        text(data.privacy(), sanitizer),
        text(data.accessibility(), sanitizer),
        text(data.legalNotice(), sanitizer));
  }

  private static @Nullable LegalText text(
      LegalTextsData.@Nullable Text text, LegalHtmlSanitizer sanitizer) {
    return text == null
        ? null
        : new LegalText(sanitizer.sanitize(text.gl()), sanitizer.sanitize(text.es()));
  }

  private static final Pattern LEGACY_EMAIL =
      Pattern.compile("[^@\\s,;:<>()]+@[^@\\s,;:<>()]+\\.[^@\\s,;:<>()]+");
  private static final Pattern LEGACY_PHONE = Pattern.compile("\\+?[0-9][0-9 ]{7,13}[0-9]");

  private static @Nullable String find(Pattern pattern, String text) {
    Matcher m = pattern.matcher(text);
    return m.find() ? m.group() : null;
  }

  private static boolean isBlank(@Nullable String value) {
    return value == null || value.isBlank();
  }

  private static LegalPageMode mode(
      List<FieldViolation> errors, String field, @Nullable String name) {
    if (isBlank(name)) {
      return LegalPageMode.FURAR;
    }
    return LegalPageMode.fromName(name)
        .orElseGet(
            () -> {
              errors.add(FieldViolation.of("legal." + field, ValidationError.PATTERN));
              return LegalPageMode.FURAR;
            });
  }

  static Theme theme(ThemeData t) {
    if (t == null) {
      return Theme.DEFAULT;
    }
    return new Theme(
        new HexColor(t.primary()),
        new HexColor(t.onPrimary()),
        new HexColor(t.accent()),
        new HexColor(t.background()),
        new HexColor(t.text()));
  }
}
