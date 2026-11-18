// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.municipality.application.service;

import gal.furar.municipality.domain.LegalDetails;
import gal.furar.municipality.domain.LegalPageSettings;
import gal.furar.municipality.domain.Municipality;
import gal.furar.municipality.domain.SportsContact;
import gal.furar.municipality.domain.Theme;
import gal.furar.shared.audit.AuditChanges;
import gal.furar.shared.audit.AuditChanges.Snapshot;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/**
 * As fotos da configuración do concello para o rexistro de actividade (ADR-0020, revisión do
 * 2026-11-10). Valores curtos e sen datos persoais: nome, idiomas, cores, os datos da entidade e os
 * enderezos web. Os correos e os teléfonos (tamén os do DPD e do contacto de deportes), o nome do
 * contacto e os textos longos (textos legais, pago e devolución) van ocultos: só «cambiou».
 */
final class MunicipalityAuditSnapshots {

  private MunicipalityAuditSnapshots() {}

  static Snapshot of(Municipality m) {
    Theme theme = m.theme();
    LegalDetails legal = m.legal();
    SportsContact contact = m.sportsContact();
    return AuditChanges.snapshot()
        .value("municipality.name", m.name())
        .value(
            "municipality.languages",
            m.languages().stream().map(l -> l.code()).collect(Collectors.joining(",")))
        .value("municipality.defaultLanguage", m.defaultLanguage().code())
        .value("municipality.theme.primary", theme.primary().value())
        .value("municipality.theme.onPrimary", theme.onPrimary().value())
        .value("municipality.theme.accent", theme.accent().value())
        .value("municipality.theme.background", theme.background().value())
        .value("municipality.theme.text", theme.text().value())
        .value("legal.legalEntityName", legal.legalEntityName())
        .value("legal.nif", legal.nif())
        .value("legal.address", legal.address())
        .value("legal.eOfficeUrl", legal.eOfficeUrl())
        .hidden("legal.generalEmail", legal.generalEmail())
        .hidden("legal.generalPhone", legal.generalPhone())
        .hidden("legal.dpoEmail", legal.dpoEmail())
        .hidden("legal.dpoPhone", legal.dpoPhone())
        .value("legal.processor", legal.processor())
        .hidden("legal.accessibilityEmail", legal.accessibilityEmail())
        .value("legal.accessibilityComplaintUrl", legal.accessibilityComplaintUrl())
        .value("legal.accessibilityUnit", legal.accessibilityUnit())
        .value("legal.processingRegisterUrl", legal.processingRegisterUrl())
        .value("legal.useRegulationUrl", legal.useRegulationUrl())
        .value("legal.privacy", page(legal.privacy()))
        .value("legal.accessibility", page(legal.accessibility()))
        .value("legal.legalNotice", page(legal.legalNotice()))
        .hidden("legal.texts.privacy", m.legalTexts().privacy())
        .hidden("legal.texts.accessibility", m.legalTexts().accessibility())
        .hidden("legal.texts.legalNotice", m.legalTexts().legalNotice())
        .hidden("sports.name", contact.name())
        .value("sports.address", contact.address())
        .hidden("sports.phone", contact.phone())
        .hidden("sports.email", contact.email())
        .value("payment.scope", m.paymentNotes().scope().name())
        .hidden("payment.payment", m.paymentNotes().payment())
        .hidden("payment.refund", m.paymentNotes().refund())
        .value("noShow.penalty", m.noShowPolicy().enabled())
        .value("noShow.threshold", m.noShowPolicy().threshold())
        .value("noShow.windowDays", m.noShowPolicy().windowDays())
        .value("noShow.blockDays", m.noShowPolicy().blockDays())
        .value("openData", m.openData())
        .value("openMatch.enabled", m.openMatchPolicy().enabled())
        .value("openMatch.cancelHours", m.openMatchPolicy().cancelHours())
        .value("installHint", m.installHint());
  }

  /** O modo dunha páxina legal e, se vai fóra, o seu enderezo. */
  private static @Nullable String page(@Nullable LegalPageSettings page) {
    if (page == null) {
      return null;
    }
    return page.url() == null ? page.mode().name() : page.mode().name() + " " + page.url();
  }
}
