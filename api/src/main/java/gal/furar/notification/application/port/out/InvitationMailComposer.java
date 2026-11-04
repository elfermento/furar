// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.notification.application.port.out;

import gal.furar.municipality.MunicipalityMailContext;
import gal.furar.notification.StaffInvitationMail;

/** Escribe o correo dunha invitación ao persoal (asunto, HTML e texto) co concello que invita. */
public interface InvitationMailComposer {

  OutgoingMail compose(StaffInvitationMail mail, MunicipalityMailContext municipality);
}
