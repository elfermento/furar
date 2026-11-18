// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.incident.adapter.in.event;

import gal.furar.incident.IncidentReported;
import gal.furar.incident.IncidentResolved;
import gal.furar.incident.application.port.in.SendIncidentMails;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Escoita as incidencias despois do commit, nun fío propio e no concello do evento (ADR-0006,
 * ADR-0036): unha nova avisa á xestión; un aviso resolto, á persoa. Un SMTP caído nunca desfai o
 * aviso nin o cambio: o envío queda rexistrado como fallido.
 */
@Component
class IncidentMailListener {

  private final SendIncidentMails mails;

  IncidentMailListener(SendIncidentMails mails) {
    this.mails = mails;
  }

  @Async
  @TransactionalEventListener
  void on(IncidentReported event) {
    mails.reported(event.incidentId());
  }

  @Async
  @TransactionalEventListener
  void on(IncidentResolved event) {
    mails.resolved(event.incidentId());
  }
}
