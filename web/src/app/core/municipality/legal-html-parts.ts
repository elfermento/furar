// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { LegalDetailsResponse } from '../../api/models/legal-details-response';
import type { PrivacyNoticeResponse } from '../../api/models/privacy-notice-response';
import { placeOf } from '../../shared/i18n/place-name';
import type { IsoPeriod } from '../../shared/ui/calendar/iso-period';
import { parseIsoPeriod, periodText } from '../../shared/ui/calendar/iso-period';

/* Pezas comúns dos textos legais de Furar (`furar-legal-html.ts`, `furar-privacy-html.ts`). */

/** Datos cos que Furar escribe as súas páxinas legais: os gardados ou os do formulario. */
export interface FurarLegalData {
  /** Nome do concello («Narón»). */
  readonly name: string;
  /** Idioma do texto: o nome vai con «de» ou coa contracción galega («do Concello da Coruña»). */
  readonly lang: string;
  readonly legal: Partial<LegalDetailsResponse>;
  /**
   * Os prazos de conservación do despregue (só para «Privacidade»): rexistro de actividade e
   * reservas. O resto da cláusula (responsable, DPD, dereitos, encargado) sae dos datos legais.
   */
  readonly notice: PrivacyNoticeResponse | null;
  /** Se o despregue ten mapa (accesibilidade: exclusión do art. 3.4.d do RD 1112/2018). */
  readonly mapEnabled?: boolean;
  /** O concello publica datos abertos (ADR-0034, punto 28): o aviso legal e a privacidade din como. */
  readonly openData?: boolean;
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
export const escape = (text: string): string => text.replace(/[&<>"']/g, (c) => ESCAPES[c] ?? c);
const EMAIL = /^[^@\s]+@[^@\s]+\.[^@\s]+$/;
const HTTPS = /^https:\/\/\S+$/i;

export const clean = (value: string | undefined): string => value?.trim() ?? '';
export const p = (text: string): string => `<p>${escape(text)}</p>`;
export const h2 = (text: string, id?: string): string => `<h2${id ? ` id="${id}"` : ''}>${escape(text)}</h2>`;
export const h3 = (text: string): string => `<h3>${escape(text)}</h3>`;
export const item = (text: string): string => `<li>${escape(text)}</li>`;
/** Unha lista só se ten elementos (os baleiros xa veñen como ''). */
export const list = (items: readonly string[]): string => (items.some(Boolean) ? `<ul>${items.join('')}</ul>` : '');

// As anclas viven á parte (`legal-anchors.ts`): o shell só precisa unha e non debe cargar estas pezas.
export {
  CONDITIONS_LEGAL_ANCHOR,
  COOKIES_PRIVACY_ANCHOR,
  LEGAL_ANCHORS,
  SOFTWARE_LEGAL_ANCHOR,
  STAFF_PRIVACY_ANCHOR,
} from './legal-anchors';

/** Ligazón só se o valor é un correo ou un https (os datos legais xa veñen validados). */
export function link(label: string, value: string, wrap: 'p' | 'li'): string {
  const href = EMAIL.test(value) ? `mailto:${value}` : HTTPS.test(value) ? value : '';
  const target = href ? `<a href="${escape(href)}">${escape(value)}</a>` : escape(value);
  return `<${wrap}>${escape(label)} ${target}</${wrap}>`;
}

/** A entidade responsable e titular: a dos datos legais ou, sen ela, «Concello de…». */
export function ownerOf(data: FurarLegalData, t: Translate): string {
  return clean(data.legal.legalEntityName) || t('legalNotice.ownerDefault', { ofName: placeOf(data.name, data.lang) });
}

/** O prazo en texto («3 anos»); se a API non o di ben, `years` anos. */
export function period(text: string | undefined, years: number, t: Translate): string {
  const fallback: IsoPeriod = { years, months: 0, days: 0 };
  const { key, count } = periodText(parseIsoPeriod(text) ?? fallback) ?? {
    key: 'common.period.yearsOther',
    count: years,
  };
  return t(key, { count: String(count) });
}
