// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.incident.application.service;

import gal.furar.facility.FacilityQueries;
import gal.furar.facility.FacilitySummary;
import gal.furar.incident.application.port.in.SendIncidentMails;
import gal.furar.incident.application.port.out.IncidentRepository;
import gal.furar.incident.domain.Incident;
import gal.furar.incident.domain.IncidentId;
import gal.furar.notification.IncidentMailer;
import gal.furar.notification.IncidentReportedMail;
import gal.furar.notification.IncidentResolvedMail;
import gal.furar.staff.StaffQueries;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Prepara os correos dunha incidencia nunha lectura curta e envíaos despois, fóra de toda
 * transacción, con {@link IncidentMailer} (ADR-0036). «Nova incidencia» vai a cada persoa con rol
 * Xestión activa no concello (non hai outra lista de avisos da xestión), menos a quen a anotou;
 * «Resolvemos o teu aviso», á persoa que avisou, se aínda ten correo.
 */
@Service
class IncidentMails implements SendIncidentMails {

  private final IncidentRepository repository;
  private final FacilityQueries facilities;
  private final StaffQueries staff;
  private final IncidentMailer mailer;
  private final TransactionTemplate read;

  IncidentMails(
      IncidentRepository repository,
      FacilityQueries facilities,
      StaffQueries staff,
      IncidentMailer mailer,
      PlatformTransactionManager transactions) {
    this.repository = repository;
    this.facilities = facilities;
    this.staff = staff;
    this.mailer = mailer;
    this.read = new TransactionTemplate(transactions);
    this.read.setReadOnly(true);
  }

  @Override
  public void reported(UUID incidentId) {
    Optional<IncidentReportedMail> mail =
        Objects.requireNonNull(
            read.execute(
                status ->
                    repository
                        .findById(new IncidentId(incidentId))
                        .map(
                            incident ->
                                new IncidentReportedMail(
                                    incidentId,
                                    recipients(incident),
                                    facilityName(incident),
                                    incident.type().name(),
                                    incident.createdAt()))));
    mail.ifPresent(mailer::sendReported);
  }

  @Override
  public void resolved(UUID incidentId) {
    Optional<IncidentResolvedMail> mail =
        Objects.requireNonNull(
            read.execute(
                status ->
                    repository
                        .findById(new IncidentId(incidentId))
                        .filter(Incident::canNotifyReporter)
                        .map(
                            incident ->
                                new IncidentResolvedMail(
                                    incidentId,
                                    Objects.requireNonNull(incident.reporter().email()),
                                    incident.reporter().language(),
                                    incident.reporter().name(),
                                    facilityName(incident),
                                    incident.type().name(),
                                    incident.createdAt(),
                                    incident.resolutionMessage()))));
    mail.ifPresent(mailer::sendResolved);
  }

  private List<IncidentReportedMail.Recipient> recipients(Incident incident) {
    String reporter = incident.reporter().subject();
    return staff.activeManagers().stream()
        .filter(m -> m.email() != null)
        .filter(m -> reporter == null || !reporter.equals(m.subject()))
        .map(m -> new IncidentReportedMail.Recipient(m.id(), Objects.requireNonNull(m.email())))
        .toList();
  }

  private String facilityName(Incident incident) {
    return facilities.find(incident.facilityId()).map(FacilitySummary::name).orElse("");
  }
}
