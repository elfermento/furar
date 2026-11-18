// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { FurarLegalData, Translate } from './legal-html-parts';
import {
  COOKIES_PRIVACY_ANCHOR,
  STAFF_PRIVACY_ANCHOR,
  clean,
  h2,
  h3,
  item,
  link,
  list,
  ownerOf,
  p,
  period,
} from './legal-html-parts';

/**
 * Privacidade (RGPD arts. 13 e 14; LOPDGDD art. 11), en rexistro formal (revisión do 2026-11-09):
 * información básica, responsable, DPD, os tres tratamentos (contas e reservas, persoal do
 * concello, seguridade), destinatarios, conservación cos prazos reais do despregue, dereitos,
 * reclamación e, se o hai, o rexistro de actividades de tratamento. Cada dato sae só se o hai.
 * «Cookies» vai á parte (`furarCookiesHtml`): sae sempre, tamén cun texto propio.
 */
export function furarPrivacyHtml(data: FurarLegalData, t: Translate): string {
  const l = data.legal;
  const owner = ownerOf(data, t);
  const nif = clean(l.nif);
  const address = clean(l.address);
  const email = clean(l.generalEmail);
  const phone = clean(l.generalPhone);
  const office = clean(l.eOfficeUrl);
  const dpoEmail = clean(l.dpoEmail);
  const dpoPhone = clean(l.dpoPhone);
  const processor = clean(l.processor);
  const register = clean(l.processingRegisterUrl);
  const dpo = list([
    dpoEmail ? link(t('legalNotice.email'), dpoEmail, 'li') : '',
    dpoPhone ? item(t('privacy.phone', { value: dpoPhone })) : '',
  ]);
  const channels = list([
    office ? link(t('legalNotice.office'), office, 'li') : '',
    email ? link(t('legalNotice.email'), email, 'li') : '',
    dpoEmail && dpoEmail !== email ? link(t('privacy.rightsDpo'), dpoEmail, 'li') : '',
    address ? item(t('privacy.rightsAddress', { address })) : '',
  ]);
  return [
    p(t('privacy.intro')),
    h2(t('privacy.basicTitle')),
    list([
      item(t('privacy.basic.controller', { owner })),
      item(t('privacy.basic.purpose')),
      item(t('privacy.basic.legitimacy')),
      item(processor ? t('privacy.basic.recipientsProcessor', { processor }) : t('privacy.basic.recipients')),
      item(t('privacy.basic.rights')),
    ]),
    h2(t('privacy.controllerTitle')),
    p(t('privacy.controller', { owner })),
    list([
      nif ? item(t('legalNotice.nif', { value: nif })) : '',
      address ? item(t('legalNotice.address', { value: address })) : '',
      email ? link(t('legalNotice.email'), email, 'li') : '',
      phone ? item(t('privacy.phone', { value: phone })) : '',
      office ? link(t('legalNotice.office'), office, 'li') : '',
    ]),
    dpo ? h2(t('privacy.dpoTitle')) + p(t('privacy.dpo', { owner })) + dpo : '',
    residents(t, data.openData === true),
    staff(data, t),
    security(t),
    h2(t('privacy.recipientsTitle')),
    p(t('privacy.recipients')),
    processor ? p(t('privacy.recipientsProcessor', { processor })) : '',
    p(t('privacy.recipientsServices')),
    p(t('privacy.recipientsAccess')),
    retention(data, t),
    h2(t('privacy.rightsTitle')),
    p(t('privacy.rights', { owner })),
    channels,
    p(t('privacy.rightsDeadline')),
    p(t('privacy.rightsBookings')),
    p(t('privacy.rightsPortability')),
    h2(t('privacy.complaintTitle')),
    p(t('privacy.complaint')),
    link(t('privacy.complaintOffice'), 'https://sede.aepd.gob.es', 'p'),
    dpoEmail ? p(t('privacy.complaintDpo')) : '',
    register ? h2(t('privacy.registerTitle')) + link(t('privacy.register'), register, 'p') : '',
  ].join('');
}

/** Contas e reservas: finalidade, bases (6.1.e e, coas taxas, 6.1.c), datos, procedencia e idade. */
function residents(t: Translate, openData: boolean): string {
  return [
    h2(t('privacy.residents.heading')),
    h3(t('privacy.purposeTitle')),
    p(t('privacy.residents.purpose')),
    h3(t('privacy.basisTitle')),
    list([item(t('privacy.residents.basisPublic')), item(t('privacy.residents.basisLegal'))]),
    p(t('privacy.residents.noConsent')),
    h3(t('privacy.dataTitle')),
    list([
      item(t('privacy.residents.dataAccount')),
      item(t('privacy.residents.dataBookings')),
      item(t('privacy.residents.dataFields')),
      item(t('privacy.residents.dataOnBehalf')),
      item(t('privacy.residents.dataDependents')),
      item(t('privacy.residents.dataAttendance')),
      item(t('privacy.residents.dataWaits')),
      item(t('privacy.residents.dataOpenMatches')),
      item(t('privacy.residents.dataOrganizations')),
    ]),
    openData ? p(t('privacy.residents.openData')) : '',
    h3(t('privacy.sourceTitle')),
    p(t('privacy.residents.source')),
    h3(t('privacy.automatedTitle')),
    p(t('privacy.residents.automated')),
    h3(t('privacy.minorsTitle')),
    p(t('privacy.residents.minors')),
  ].join('');
}

/**
 * O tratamento do persoal do concello (segunda capa do aviso da invitación): datos, finalidade e
 * base, conservación do rexistro de actividade cos prazos do despregue (LOPDGDD art. 32) e
 * destinatarios dos eventos bloqueados.
 */
function staff(data: FurarLegalData, t: Translate): string {
  const visible = period(data.notice?.activityVisible, 1, t);
  const retention = period(data.notice?.activityRetention, 3, t);
  return [
    h2(t('privacy.staff.heading'), STAFF_PRIVACY_ANCHOR),
    h3(t('privacy.controllerTitle')),
    p(t('privacy.staff.controller', { owner: ownerOf(data, t) })),
    h3(t('privacy.dataTitle')),
    p(t('privacy.staff.data')),
    h3(t('privacy.purposeTitle')),
    p(t('privacy.staff.purpose')),
    h3(t('privacy.retentionTitle')),
    p(t('privacy.staff.retention', { visible, retention })),
    h3(t('privacy.recipientsTitle')),
    p(t('privacy.staff.recipients')),
  ].join('');
}

/** Seguridade: accesos, límite de peticións por IP (en memoria) e rexistros técnicos. */
function security(t: Translate): string {
  return [
    h2(t('privacy.security.heading')),
    h3(t('privacy.purposeTitle')),
    p(t('privacy.security.purpose')),
    h3(t('privacy.basisTitle')),
    p(t('privacy.security.basis')),
    h3(t('privacy.dataTitle')),
    p(t('privacy.security.data')),
    p(t('privacy.security.rateLimit')),
  ].join('');
}

/** Conservación: as reservas, cos prazos reais do despregue (`furar.booking.*`); o resto, fixo. */
function retention(data: FurarLegalData, t: Translate): string {
  const bookings = period(data.notice?.bookingRetention, 2, t);
  const paid = period(data.notice?.paidBookingRetention, 5, t);
  return [
    h2(t('privacy.retentionTitle')),
    list([
      item(t('privacy.retention.account')),
      item(t('privacy.retention.bookings', { bookings, paid })),
      item(t('privacy.retention.dependents')),
      item(t('privacy.retention.waits')),
      item(t('privacy.retention.openMatches')),
      item(t('privacy.retention.organizations')),
      item(t('privacy.retention.mail')),
      item(t('privacy.retention.access')),
      item(t('privacy.retention.logs')),
      item(t('privacy.retention.backups')),
      item(t('privacy.retention.staff')),
    ]),
    p(t('privacy.retention.blocked')),
  ].join('');
}

/**
 * «Cookies» de Privacidade (LSSI art. 22.2): só almacenamento técnico, sen banner. Describe o
 * software, non o concello: sae sempre na páxina «Privacidade», tamén cun texto ou unha páxina
 * propia, e a ela leva a ligazón «Cookies» do pé.
 */
export function furarCookiesHtml(t: Translate): string {
  return [
    h2(t('privacy.cookies.title'), COOKIES_PRIVACY_ANCHOR),
    p(t('privacy.cookies.intro')),
    list([
      item(t('privacy.cookies.session')),
      item(t('privacy.cookies.local')),
      item(t('privacy.cookies.temporary')),
      item(t('privacy.cookies.cache')),
    ]),
    p(t('privacy.cookies.none')),
    p(t('privacy.cookies.manage')),
  ].join('');
}
