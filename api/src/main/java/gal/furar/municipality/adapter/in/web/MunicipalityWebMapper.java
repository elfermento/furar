// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.municipality.adapter.in.web;

import gal.furar.municipality.adapter.in.web.dto.CreateMunicipalityRequest;
import gal.furar.municipality.adapter.in.web.dto.LegalDetailsRequest;
import gal.furar.municipality.adapter.in.web.dto.LegalDetailsResponse;
import gal.furar.municipality.adapter.in.web.dto.LegalTextRequest;
import gal.furar.municipality.adapter.in.web.dto.LegalTextResponse;
import gal.furar.municipality.adapter.in.web.dto.LegalTextsRequest;
import gal.furar.municipality.adapter.in.web.dto.LegalTextsResponse;
import gal.furar.municipality.adapter.in.web.dto.MunicipalityResponse;
import gal.furar.municipality.adapter.in.web.dto.MunicipalitySummaryResponse;
import gal.furar.municipality.adapter.in.web.dto.ThemeRequest;
import gal.furar.municipality.adapter.in.web.dto.ThemeResponse;
import gal.furar.municipality.adapter.in.web.dto.UpdateMunicipalityRequest;
import gal.furar.municipality.application.port.in.CreateMunicipalityCommand;
import gal.furar.municipality.application.port.in.LegalData;
import gal.furar.municipality.application.port.in.LegalTextsData;
import gal.furar.municipality.application.port.in.MunicipalityView;
import gal.furar.municipality.application.port.in.ThemeData;
import gal.furar.municipality.application.port.in.UpdateMunicipalityCommand;
import gal.furar.municipality.domain.LegalDetails;
import gal.furar.municipality.domain.LegalText;
import gal.furar.municipality.domain.LegalTexts;
import gal.furar.municipality.domain.Municipality;
import gal.furar.municipality.domain.Theme;
import gal.furar.shared.domain.Language;
import org.jspecify.annotations.Nullable;

/** DTO ↔ comandos e vistas do concello. */
final class MunicipalityWebMapper {

  private MunicipalityWebMapper() {}

  static CreateMunicipalityCommand toCommand(CreateMunicipalityRequest r) {
    return new CreateMunicipalityCommand(
        r.slug(), r.name(), r.languages(), r.defaultLanguage(), theme(r.theme()), legal(r.legal()));
  }

  static UpdateMunicipalityCommand toCommand(long expectedVersion, UpdateMunicipalityRequest r) {
    return new UpdateMunicipalityCommand(
        expectedVersion,
        r.name(),
        r.languages(),
        r.defaultLanguage(),
        theme(r.theme()),
        r.customFields(),
        legal(r.legal()));
  }

  static MunicipalityResponse toResponse(MunicipalityView view) {
    Municipality m = view.municipality();
    return new MunicipalityResponse(
        m.id().value(),
        m.slug().value(),
        m.name(),
        m.languages().stream().map(Language::code).toList(),
        m.defaultLanguage().code(),
        theme(m.theme()),
        legal(m.legal()),
        view.visibleCustomFields().toJson(),
        m.version() == null ? 0 : m.version(),
        m.createdAt(),
        m.updatedAt());
  }

  static MunicipalitySummaryResponse toSummary(Municipality m) {
    return new MunicipalitySummaryResponse(m.id().value(), m.slug().value(), m.name());
  }

  static ThemeResponse theme(Theme t) {
    return new ThemeResponse(
        t.primary().value(),
        t.onPrimary().value(),
        t.accent().value(),
        t.background().value(),
        t.text().value());
  }

  static LegalDetailsResponse legal(LegalDetails l) {
    return new LegalDetailsResponse(
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
        l.accessibilityUnit(),
        l.privacy().mode().name(),
        l.privacy().url(),
        l.accessibility().mode().name(),
        l.accessibility().url(),
        l.legalNotice().mode().name(),
        l.legalNotice().url());
  }

  static @Nullable LegalData legal(@Nullable LegalDetailsRequest l) {
    return l == null
        ? null
        : new LegalData(
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
            l.accessibilityUnit(),
            l.privacyMode(),
            l.privacyUrl(),
            l.accessibilityMode(),
            l.accessibilityUrl(),
            l.legalNoticeMode(),
            l.legalNoticeUrl(),
            l.dpoContact());
  }

  static LegalTextsResponse legalTexts(LegalTexts t) {
    return new LegalTextsResponse(
        legalText(t.privacy()), legalText(t.accessibility()), legalText(t.legalNotice()));
  }

  private static @Nullable LegalTextResponse legalText(@Nullable LegalText t) {
    return t == null ? null : new LegalTextResponse(t.gl(), t.es());
  }

  static @Nullable LegalTextsData legalTexts(@Nullable LegalTextsRequest t) {
    return t == null
        ? null
        : new LegalTextsData(
            legalText(t.privacy()), legalText(t.accessibility()), legalText(t.legalNotice()));
  }

  private static LegalTextsData.@Nullable Text legalText(@Nullable LegalTextRequest t) {
    return t == null ? null : new LegalTextsData.Text(t.gl(), t.es());
  }

  private static ThemeData theme(ThemeRequest t) {
    return t == null
        ? null
        : new ThemeData(t.primary(), t.onPrimary(), t.accent(), t.background(), t.text());
  }
}
