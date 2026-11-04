// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.municipality.application.service;

import gal.furar.municipality.MunicipalityMailContext;
import gal.furar.municipality.MunicipalityQueries;
import gal.furar.municipality.application.port.out.MunicipalityRepository;
import gal.furar.municipality.domain.Municipality;
import gal.furar.municipality.domain.MunicipalityId;
import gal.furar.municipality.domain.PaymentNotes;
import gal.furar.municipality.domain.SportsContact;
import gal.furar.shared.domain.Language;
import gal.furar.shared.domain.LocalizedText;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Implementa {@link MunicipalityQueries} para outros módulos. */
@Service
class MunicipalityQueryService implements MunicipalityQueries {

  private final MunicipalityRepository repository;

  MunicipalityQueryService(MunicipalityRepository repository) {
    this.repository = repository;
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<MunicipalityMailContext> mailContext(UUID municipalityId) {
    return repository
        .findById(new MunicipalityId(municipalityId))
        .map(MunicipalityQueryService::of);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<String> name(UUID municipalityId) {
    return repository.findById(new MunicipalityId(municipalityId)).map(Municipality::name);
  }

  private static MunicipalityMailContext of(Municipality m) {
    SportsContact c = m.sportsContact();
    PaymentNotes n = m.paymentNotes();
    return new MunicipalityMailContext(
        m.id().value(),
        m.slug().value(),
        m.name(),
        m.languages().stream().map(Language::code).toList(),
        m.defaultLanguage().code(),
        m.theme().primary().value(),
        m.theme().onPrimary().value(),
        new MunicipalityMailContext.Contact(c.name(), c.address(), c.phone(), c.email()),
        // Por instalación, as que non teñen texto levan o de Furar, non o do concello.
        text(n.byFacility() ? PaymentNotes.DEFAULT_PAYMENT : n.effectivePayment()),
        text(n.byFacility() ? PaymentNotes.DEFAULT_REFUND : n.effectiveRefund()),
        m.legal().privacy().externalUrl(),
        m.legal().privacy().published(),
        n.byFacility());
  }

  private static MunicipalityMailContext.Text text(LocalizedText t) {
    return new MunicipalityMailContext.Text(t.gl(), t.es());
  }
}
