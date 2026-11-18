// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { FurarLegalData, Translate } from './legal-html-parts';
import {
  CONDITIONS_LEGAL_ANCHOR,
  SOFTWARE_LEGAL_ANCHOR,
  clean,
  escape,
  h2,
  h3,
  item,
  link,
  list,
  ownerOf,
  p,
} from './legal-html-parts';
import type { LegalPageKey } from './legal-pages';
import { furarPrivacyHtml } from './furar-privacy-html';

export type { FurarLegalData, Translate } from './legal-html-parts';
export {
  COOKIES_PRIVACY_ANCHOR,
  CONDITIONS_LEGAL_ANCHOR,
  LEGAL_ANCHORS,
  SOFTWARE_LEGAL_ANCHOR,
  STAFF_PRIVACY_ANCHOR,
} from './legal-html-parts';
export { furarCookiesHtml } from './furar-privacy-html';

/**
 * Modelo do RD 1112/2018 (Decisión (UE) 2018/1523), en rexistro formal. Sen datas fixas nin un
 * estado que pareza dunha revisión propia: o estado e o método son os da autoavaliación do
 * software, que se repite en cada versión (revisión do 2026-11-09). O teléfono xeral é a canle
 * complementaria (art. 11); sen el, o rexistro presencial co domicilio.
 */
function accessibility(data: FurarLegalData, t: Translate): string {
  const l = data.legal;
  const owner = ownerOf(data, t);
  const email = clean(l.accessibilityEmail);
  const phone = clean(l.generalPhone);
  const office = clean(l.eOfficeUrl);
  const address = clean(l.address);
  const unit = clean(l.accessibilityUnit);
  const complaint = clean(l.accessibilityComplaintUrl);
  const channels = list([
    email ? link(t('accessibility.contactEmail'), email, 'li') : '',
    phone ? item(t('accessibility.contactPhone', { value: phone })) : '',
    office ? link(t('accessibility.contactOffice'), office, 'li') : '',
    address ? item(t('accessibility.contactAddress', { address })) : '',
  ]);
  return [
    p(t('accessibility.intro', { owner })),
    p(t('accessibility.scope')),
    h2(t('accessibility.statusTitle')),
    p(t('accessibility.status')),
    h2(t('accessibility.nonAccessibleTitle')),
    p(t('accessibility.nonAccessibleIntro')),
    h3(t('accessibility.nonConformityTitle')),
    list([item(t('accessibility.nonAccessibleManual')), item(t('accessibility.ownContent', { owner }))]),
    h3(t('accessibility.burdenTitle')),
    p(t('accessibility.burden')),
    h3(t('accessibility.outOfScopeTitle')),
    p(data.mapEnabled ? t('accessibility.outOfScopeMap') : t('accessibility.outOfScopeNone')),
    h2(t('accessibility.preparationTitle')),
    p(t('accessibility.preparation')),
    p(t('accessibility.review')),
    h2(t('accessibility.contactTitle')),
    p(t('accessibility.contact')),
    channels || p(t('accessibility.contactDefault')),
    p(t('accessibility.requests')),
    list([item(t('accessibility.requestComplaint')), item(t('accessibility.requestInformation'))]),
    p(t('accessibility.requestDetail')),
    p(t('accessibility.requestHow')),
    h2(t('accessibility.enforcementTitle')),
    p(t('accessibility.enforcement')),
    unit ? p(t('accessibility.complaintUnit', { unit })) : p(t('accessibility.complaintUnitDefault')),
    complaint ? link(t('accessibility.complaintLink'), complaint, 'p') : '',
    p(t('accessibility.appeal')),
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
 * Condicións de uso (revisión do 2026-11-09): informativas, sen nada que aceptar, e só do que fai
 * Furar (reservar, o código, cancelar, os cambios do concello e que non cobra en liña). Rexen a
 * normativa de uso e a ordenanza fiscal do concello; a ligazón á normativa, se a hai. Furar non ten
 * regras de inasistencia: non se mencionan.
 */
function conditions(data: FurarLegalData, owner: string, t: Translate): string {
  const regulation = clean(data.legal.useRegulationUrl);
  return [
    h2(t('legalNotice.conditions.title'), CONDITIONS_LEGAL_ANCHOR),
    p(t('legalNotice.conditions.intro')),
    regulation ? link(t('legalNotice.conditions.regulation'), regulation, 'p') : '',
    h3(t('legalNotice.conditions.accountTitle')),
    p(t('legalNotice.conditions.account')),
    h3(t('legalNotice.conditions.bookingTitle')),
    p(t('legalNotice.conditions.booking', { owner })),
    p(t('legalNotice.conditions.onBehalf')),
    p(t('legalNotice.conditions.code')),
    h3(t('legalNotice.conditions.cancelTitle')),
    p(t('legalNotice.conditions.cancel')),
    h3(t('legalNotice.conditions.attendanceTitle')),
    p(t('legalNotice.conditions.attendance', { owner })),
    h3(t('legalNotice.conditions.paymentTitle')),
    p(t('legalNotice.conditions.payment')),
    h3(t('legalNotice.conditions.changesTitle')),
    p(t('legalNotice.conditions.changes', { owner })),
  ].join('');
}

/**
 * Aviso legal (LSSI art. 10; Lei 40/2015 art. 39), en rexistro formal: titular, obxecto e que non
 * é a sede electrónica, condicións de uso, contidos, responsabilidade, protección de datos, as
 * obrigas do persoal que usa a xestión (ADR-0027, revisión do 2026-11-03: Furar só as publica; a
 * súa aceptación é un procedemento propio do concello) e o software.
 */
function legalNotice(data: FurarLegalData, t: Translate): string {
  const l = data.legal;
  const owner = ownerOf(data, t);
  const nif = clean(l.nif);
  const address = clean(l.address);
  const email = clean(l.generalEmail);
  const phone = clean(l.generalPhone);
  const dir3 = clean(l.dir3Code);
  const office = clean(l.eOfficeUrl);
  return [
    h2(t('legalNotice.ownerTitle')),
    p(t('legalNotice.ownerIntro')),
    list([
      item(owner),
      nif ? item(t('legalNotice.nif', { value: nif })) : '',
      address ? item(t('legalNotice.address', { value: address })) : '',
      email ? link(t('legalNotice.email'), email, 'li') : '',
      phone ? item(t('legalNotice.phone', { value: phone })) : '',
      dir3 ? item(t('legalNotice.dir3', { value: dir3 })) : '',
      office ? link(t('legalNotice.office'), office, 'li') : '',
    ]),
    h2(t('legalNotice.purposeTitle')),
    p(t('legalNotice.purpose')),
    office ? link(t('legalNotice.notOffice'), office, 'p') : p(t('legalNotice.notOfficeGeneric')),
    conditions(data, owner, t),
    h2(t('legalNotice.contentsTitle')),
    p(t('legalNotice.contents')),
    data.openData ? p(t('legalNotice.openData', { owner })) : '',
    h2(t('legalNotice.liabilityTitle')),
    p(t('legalNotice.liability', { owner })),
    h2(t('legalNotice.dataTitle')),
    p(t('legalNotice.data')),
    h2(t('legalNotice.staffTitle')),
    p(t('legalNotice.staffIntro')),
    `<ul>${STAFF_OBLIGATIONS.map((key) => item(t(key))).join('')}</ul>`,
    p(t('legalNotice.staffLog')),
    h2(t('legalNotice.softwareTitle'), SOFTWARE_LEGAL_ANCHOR),
    p(t('legalNotice.software')),
    // O código, a licenza e as licenzas de terceiros (ficheiros servidos á parte, non rutas da web).
    `<ul>${SOFTWARE_FILES.map(([href, key]) => `<li><a href="${href}">${escape(t(key))}</a></li>`).join('')}</ul>`,
  ].join('');
}

/** «Software» do aviso legal: o código fonte, a licenza e as licenzas de terceiros. */
const SOFTWARE_FILES: readonly (readonly [string, string])[] = [
  ['/codigo-fonte', 'legalNotice.softwareSource'],
  ['/LICENSE.txt', 'legalNotice.softwareLicense'],
  ['/3rdpartylicenses.txt', 'legalNotice.softwareThirdParty'],
];

/**
 * O texto que escribe Furar para unha páxina legal, en HTML da mesma lista branca ca o texto propio
 * (h2, h3, p, ul, ol, li, a…): a web pinta con el a «Páxina de Furar», a vista previa e o punto de
 * partida do «Texto propio». Todo valor vai escapado; as ligazóns, só a correos e a https.
 */
export function furarLegalHtml(page: LegalPageKey, data: FurarLegalData, t: Translate): string {
  switch (page) {
    case 'privacy':
      return furarPrivacyHtml(data, t);
    case 'accessibility':
      return accessibility(data, t);
    case 'legalNotice':
      return legalNotice(data, t);
  }
}
