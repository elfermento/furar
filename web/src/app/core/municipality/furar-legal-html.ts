// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { LegalDetailsResponse } from '../../api/models/legal-details-response';
import type { PrivacyNoticeResponse } from '../../api/models/privacy-notice-response';
import { placeOf } from '../../shared/i18n/place-name';
import type { IsoPeriod } from '../../shared/ui/calendar/iso-period';
import { parseIsoPeriod, periodText } from '../../shared/ui/calendar/iso-period';
import type { LegalPageKey } from './legal-pages';

/** Datos cos que Furar escribe as súas páxinas legais: os gardados ou os do formulario. */
export interface FurarLegalData {
  /** Nome do concello («Narón»). */
  readonly name: string;
  /** Idioma do texto: o nome vai con «de» ou coa contracción galega («do Concello da Coruña»). */
  readonly lang: string;
  readonly legal: Partial<LegalDetailsResponse>;
  /**
   * Os prazos do rexistro de actividade do despregue (só para «Privacidade»). O resto da cláusula
   * (responsable, DPD, dereitos, encargado) sae dos datos legais do concello.
   */
  readonly notice: PrivacyNoticeResponse | null;
}

/** Tradución nun idioma concreto (Transloco `translate(clave, parámetros, idioma)`). */
export type Translate = (key: string, params?: Record<string, string>) => string;

const ESCAPES: Readonly<Record<string, string>> = {
  '&': '&amp;',
  '<': '&lt;',
  '>': '&gt;',
  '"': '&quot;',
  "'": '&#39;',
};
const escape = (text: string): string => text.replace(/[&<>"']/g, (c) => ESCAPES[c] ?? c);
const EMAIL = /^[^@\s]+@[^@\s]+\.[^@\s]+$/;
const HTTPS = /^https:\/\/\S+$/i;

const clean = (value: string | undefined): string => value?.trim() ?? '';
const p = (text: string): string => `<p>${escape(text)}</p>`;
const h2 = (text: string, id?: string): string => `<h2${id ? ` id="${id}"` : ''}>${escape(text)}</h2>`;

/**
 * Ancla da sección «Persoal do concello» en Privacidade: a ela leva o correo da invitación
 * (`StaffInvitationMailComposer.STAFF_SECTION`, RGPD art. 14). Fixa: cambiala rompe os correos xa
 * enviados.
 */
export const STAFF_PRIVACY_ANCHOR = 'persoal';

/** Ancla da sección «Software» do aviso legal: a ela leva a marca do pé. */
export const SOFTWARE_LEGAL_ANCHOR = 'software';

/** Ligazón só se o valor é un correo ou un https (os datos legais xa veñen validados). */
function link(label: string, value: string, wrap: 'p' | 'li'): string {
  const href = EMAIL.test(value) ? `mailto:${value}` : HTTPS.test(value) ? value : '';
  const target = href ? `<a href="${escape(href)}">${escape(value)}</a>` : escape(value);
  return `<${wrap}>${escape(label)} ${target}</${wrap}>`;
}

/**
 * Privacidade (RGPD art. 13) cos datos legais do concello (revisión do 2026-11-03: nada do
 * despregue): responsable (denominación ou «Concello de…», NIF, domicilio e correo), DPD, encargado
 * e canles dos dereitos. Cada dato sae só se o hai; sen DPD, sen a súa sección.
 */
function privacy(data: FurarLegalData, t: Translate): string {
  const l = data.legal;
  const ofName = placeOf(data.name, data.lang);
  const item = (text: string) => `<li>${escape(text)}</li>`;
  const owner = clean(l.legalEntityName) || t('legalNotice.ownerDefault', { ofName });
  const nif = clean(l.nif);
  const address = clean(l.address);
  const email = clean(l.generalEmail);
  const office = clean(l.eOfficeUrl);
  const dpoEmail = clean(l.dpoEmail);
  const dpoPhone = clean(l.dpoPhone);
  const processor = clean(l.processor);
  const controllerDetails = [
    nif ? item(t('legalNotice.nif', { value: nif })) : '',
    address ? item(t('legalNotice.address', { value: address })) : '',
    email ? link(t('legalNotice.email'), email, 'li') : '',
  ].join('');
  const dpo = [
    dpoEmail ? link(t('legalNotice.email'), dpoEmail, 'li') : '',
    dpoPhone ? item(t('privacy.phone', { value: dpoPhone })) : '',
  ].join('');
  const channels = [
    office ? link(t('legalNotice.office'), office, 'li') : '',
    email ? link(t('legalNotice.email'), email, 'li') : '',
    dpoEmail && dpoEmail !== email ? link(t('privacy.rightsDpo'), dpoEmail, 'li') : '',
  ].join('');
  return [
    h2(t('privacy.controllerTitle')),
    p(t('privacy.controller', { owner })),
    controllerDetails ? `<ul>${controllerDetails}</ul>` : '',
    dpo ? h2(t('privacy.dpoTitle')) + `<ul>${dpo}</ul>` : '',
    h2(t('privacy.purposeTitle')),
    p(t('privacy.purpose')),
    h2(t('privacy.dataTitle')),
    p(t('privacy.data')),
    h2(t('privacy.recipientsTitle')),
    p(processor ? t('privacy.recipientsProcessor', { processor }) : t('privacy.recipients')),
    h2(t('privacy.retentionTitle')),
    p(t('privacy.retention')),
    h2(t('privacy.rightsTitle')),
    p(t('privacy.rights')),
    channels ? `<ul>${channels}</ul>` : '',
    p(t('privacy.complaint')),
    h2(t('privacy.storageTitle')),
    p(t('privacy.storage')),
    staff(data, t),
  ].join('');
}

/** O prazo en texto («3 anos»); se a API non o di ben, `years` anos. */
function period(text: string | undefined, years: number, t: Translate): string {
  const fallback: IsoPeriod = { years, months: 0, days: 0 };
  const { key, count } = periodText(parseIsoPeriod(text) ?? fallback) ?? {
    key: 'common.period.yearsOther',
    count: years,
  };
  return t(key, { count: String(count) });
}

/**
 * O tratamento do persoal do concello (segunda capa do aviso da invitación): datos, finalidade e
 * base, conservación do rexistro de actividade cos prazos do despregue (LOPDGDD art. 32) e
 * destinatarios dos eventos bloqueados.
 */
function staff(data: FurarLegalData, t: Translate): string {
  const visible = period(data.notice?.activityVisible, 1, t);
  const retention = period(data.notice?.activityRetention, 3, t);
  const h3 = (text: string): string => `<h3>${escape(text)}</h3>`;
  return [
    h2(t('privacy.staff.heading'), STAFF_PRIVACY_ANCHOR),
    h3(t('privacy.controllerTitle')),
    p(t('privacy.staff.controller', { ofName: placeOf(data.name, data.lang) })),
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

/** Modelo do RD 1112/2018 (Decisión (UE) 2018/1523); o estado actualízase tras cada revisión. */
function accessibility(data: FurarLegalData, t: Translate): string {
  const ofName = placeOf(data.name, data.lang);
  const email = clean(data.legal.accessibilityEmail);
  const office = clean(data.legal.eOfficeUrl);
  const unit = clean(data.legal.accessibilityUnit);
  const complaint = clean(data.legal.accessibilityComplaintUrl);
  return [
    p(t('accessibility.intro', { ofName })),
    p(t('accessibility.scope')),
    h2(t('accessibility.statusTitle')),
    p(t('accessibility.status')),
    h2(t('accessibility.nonAccessibleTitle')),
    `<ul><li>${escape(t('accessibility.nonAccessibleManual'))}</li><li>${escape(t('accessibility.nonAccessibleBurden'))}</li></ul>`,
    h2(t('accessibility.preparationTitle')),
    p(t('accessibility.preparation')),
    h2(t('accessibility.contactTitle')),
    p(t('accessibility.contact', { ofName })),
    email ? link(t('accessibility.contactEmail'), email, 'p') : '',
    office ? link(t('accessibility.contactOffice'), office, 'p') : '',
    h2(t('accessibility.enforcementTitle')),
    p(t('accessibility.enforcement')),
    unit ? p(t('accessibility.complaintUnit', { unit })) : p(t('accessibility.complaintUnitDefault', { ofName })),
    complaint ? link(t('accessibility.complaintLink'), complaint, 'p') : '',
  ].join('');
}

/** As obrigas do persoal (ENS op.acc.6 e mp.per.2), na orde do texto. */
const STAFF_OBLIGATIONS = [
  'legalNotice.staffItems.credentials',
  'legalNotice.staffItems.confidentiality',
  'legalNotice.staffItems.purpose',
  'legalNotice.staffItems.report',
  'legalNotice.staffItems.signOut',
] as const;

/**
 * LSSI art. 10: titular, NIF, domicilio e correo (borrador pendente de revisión legal,
 * docs/datos-persoais.md), e as obrigas do persoal que usa a xestión (ADR-0027, revisión do
 * 2026-11-03): Furar só as publica; a súa aceptación é un procedemento propio do concello.
 */
function legalNotice(data: FurarLegalData, t: Translate): string {
  const l = data.legal;
  const item = (text: string) => `<li>${escape(text)}</li>`;
  const nif = clean(l.nif);
  const address = clean(l.address);
  const email = clean(l.generalEmail);
  const dir3 = clean(l.dir3Code);
  const office = clean(l.eOfficeUrl);
  return [
    h2(t('legalNotice.ownerTitle')),
    '<ul>',
    item(clean(l.legalEntityName) || t('legalNotice.ownerDefault', { ofName: placeOf(data.name, data.lang) })),
    nif ? item(t('legalNotice.nif', { value: nif })) : '',
    address ? item(t('legalNotice.address', { value: address })) : '',
    email ? link(t('legalNotice.email'), email, 'li') : '',
    dir3 ? item(t('legalNotice.dir3', { value: dir3 })) : '',
    office ? link(t('legalNotice.office'), office, 'li') : '',
    '</ul>',
    h2(t('legalNotice.staffTitle')),
    p(t('legalNotice.staffIntro')),
    `<ul>${STAFF_OBLIGATIONS.map((key) => item(t(key))).join('')}</ul>`,
    h2(t('legalNotice.softwareTitle'), SOFTWARE_LEGAL_ANCHOR),
    p(t('legalNotice.software')),
  ].join('');
}

/**
 * O texto que escribe Furar para unha páxina legal, en HTML da mesma lista branca ca o texto propio
 * (h2, h3, p, ul, ol, li, a…): a web pinta con el a «Páxina de Furar», a vista previa e o punto de
 * partida do «Texto propio». Todo valor vai escapado; as ligazóns, só a correos e a https.
 */
export function furarLegalHtml(page: LegalPageKey, data: FurarLegalData, t: Translate): string {
  switch (page) {
    case 'privacy':
      return privacy(data, t);
    case 'accessibility':
      return accessibility(data, t);
    case 'legalNotice':
      return legalNotice(data, t);
  }
}
