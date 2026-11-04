// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.municipality.adapter.out.persistence;

import gal.furar.municipality.domain.HexColor;
import gal.furar.municipality.domain.LegalDetails;
import gal.furar.municipality.domain.LegalPageMode;
import gal.furar.municipality.domain.LegalPageSettings;
import gal.furar.municipality.domain.LegalText;
import gal.furar.municipality.domain.LegalTexts;
import gal.furar.municipality.domain.Municipality;
import gal.furar.municipality.domain.MunicipalityId;
import gal.furar.municipality.domain.PaymentNotes;
import gal.furar.municipality.domain.PaymentNotesScope;
import gal.furar.municipality.domain.Slug;
import gal.furar.municipality.domain.SportsContact;
import gal.furar.municipality.domain.Theme;
import gal.furar.shared.domain.Language;
import gal.furar.shared.domain.LocalizedText;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/** Dominio ↔ entidade JPA, a man. */
final class MunicipalityPersistenceMapper {

  private MunicipalityPersistenceMapper() {}

  static MunicipalityEntity newEntity(Municipality m) {
    var entity = new MunicipalityEntity(m.id().value(), m.slug().value(), m.createdAt());
    apply(entity, m);
    return entity;
  }

  static void apply(MunicipalityEntity e, Municipality m) {
    e.apply(
        m.name(),
        m.languages().stream().map(Language::code).toList(),
        m.defaultLanguage().code(),
        theme(m.theme()),
        m.updatedAt());
    LegalDetails l = m.legal();
    e.applyLegal(
        l.ineCode(),
        l.dir3Code(),
        l.legalEntityName(),
        l.eOfficeUrl(),
        l.nif(),
        l.address(),
        l.generalEmail(),
        l.dpoEmail(),
        l.dpoPhone(),
        l.processor(),
        l.accessibilityEmail(),
        l.accessibilityComplaintUrl(),
        l.accessibilityUnit());
    LegalTexts texts = m.legalTexts();
    e.applyPrivacyPage(l.privacy().mode().name(), l.privacy().url(), legalText(texts.privacy()));
    e.applyAccessibilityPage(
        l.accessibility().mode().name(), l.accessibility().url(), legalText(texts.accessibility()));
    e.applyLegalNoticePage(
        l.legalNotice().mode().name(), l.legalNotice().url(), legalText(texts.legalNotice()));
    SportsContact c = m.sportsContact();
    e.applySportsContact(c.name(), c.address(), c.phone(), c.email());
    PaymentNotes n = m.paymentNotes();
    e.applyPaymentNotes(n.scope().name(), text(n.payment()), text(n.refund()));
  }

  private static @Nullable Map<String, String> text(@Nullable LocalizedText t) {
    if (t == null) {
      return null;
    }
    Map<String, String> m = new LinkedHashMap<>();
    m.put("gl", t.gl());
    m.put("es", t.es());
    return m;
  }

  private static @Nullable LocalizedText text(@Nullable Map<String, String> m) {
    return m == null ? null : new LocalizedText(m.get("gl"), m.get("es"));
  }

  /** Texto propio dunha páxina legal: {@code {"gl": …, "es": …}}, cada idioma se o hai. */
  private static @Nullable Map<String, String> legalText(@Nullable LegalText t) {
    if (t == null) {
      return null;
    }
    Map<String, String> m = new LinkedHashMap<>();
    if (t.gl() != null) {
      m.put("gl", t.gl());
    }
    if (t.es() != null) {
      m.put("es", t.es());
    }
    return m;
  }

  private static @Nullable LegalText legalText(@Nullable Map<String, String> m) {
    return m == null ? null : new LegalText(m.get("gl"), m.get("es"));
  }

  private static LegalDetails legal(MunicipalityEntity e) {
    return new LegalDetails(
        e.ineCode(),
        e.dir3Code(),
        e.legalEntityName(),
        e.eOfficeUrl(),
        e.nif(),
        e.address(),
        e.generalEmail(),
        e.dpoEmail(),
        e.dpoPhone(),
        e.processor(),
        e.accessibilityEmail(),
        e.accessibilityComplaintUrl(),
        e.accessibilityUnit(),
        page(e.privacyMode(), e.privacyUrl()),
        page(e.accessibilityMode(), e.accessibilityUrl()),
        page(e.legalNoticeMode(), e.legalNoticeUrl()));
  }

  private static LegalPageSettings page(String mode, @Nullable String url) {
    return new LegalPageSettings(LegalPageMode.fromName(mode).orElseThrow(), url);
  }

  private static LegalTexts legalTexts(MunicipalityEntity e) {
    return new LegalTexts(
        legalText(e.privacyText()),
        legalText(e.accessibilityText()),
        legalText(e.legalNoticeText()));
  }

  static Municipality toDomain(MunicipalityEntity e) {
    return Municipality.rehydrate(
        new MunicipalityId(e.getId()),
        new Slug(e.slug()),
        e.name(),
        languages(e.languages()),
        Language.fromCode(e.defaultLanguage()).orElseThrow(),
        theme(e.theme()),
        legal(e),
        legalTexts(e),
        new SportsContact(
            e.sportsServiceName(), e.sportsAddress(), e.sportsPhone(), e.sportsEmail()),
        new PaymentNotes(
            PaymentNotesScope.fromName(e.paymentNotesScope()).orElseThrow(),
            text(e.paymentNote()),
            text(e.refundNote())),
        e.version(),
        e.createdAt(),
        e.updatedAt());
  }

  private static List<Language> languages(List<String> codes) {
    return codes.stream().map(c -> Language.fromCode(c).orElseThrow()).toList();
  }

  private static Map<String, String> theme(Theme t) {
    Map<String, String> m = new LinkedHashMap<>();
    m.put("primary", t.primary().value());
    m.put("onPrimary", t.onPrimary().value());
    m.put("accent", t.accent().value());
    m.put("background", t.background().value());
    m.put("text", t.text().value());
    return m;
  }

  private static Theme theme(Map<String, String> m) {
    return new Theme(
        new HexColor(m.get("primary")),
        new HexColor(m.get("onPrimary")),
        new HexColor(m.get("accent")),
        new HexColor(m.get("background")),
        new HexColor(m.get("text")));
  }
}
