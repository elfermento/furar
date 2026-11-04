// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { AbstractControl, ValidatorFn } from '@angular/forms';
import { FormControl, FormGroup } from '@angular/forms';
import type { LegalPageKey, LegalPageMode } from '../../core/municipality/legal-pages';
import { LEGAL_PAGE_FIELDS, LEGAL_PAGES } from '../../core/municipality/legal-pages';
import { OPENING_LEGAL } from '../../core/municipality/opening';
import type {
  LegalDetailsRequest,
  LegalDetailsResponse,
  LegalTextsRequest,
  LegalTextsResponse,
} from './municipality.store';

/**
 * «Datos legais» nos axustes do concello (ADR-0017 punto 8), coas mesmas regras ca `LegalDetails`
 * e `LegalTexts` da API: todo opcional; as URL só https; a páxina propia pide o seu enderezo só no
 * modo «Páxina propia». O modo, o enderezo e o texto son controis distintos: cambiar de modo non
 * borra nada. Os erros levan texto propio (`errors.legal.*`), coma na maqueta.
 */
export const LEGAL_LIMITS = { text: 200, url: 500, html: 30_000 } as const;

const HTTPS = /^https:\/\/\S+$/i;
const EMAIL = /^[^@\s]+@[^@\s]+\.[^@\s]+$/;
const INE = /^[0-9]{5}$/;
const DIR3 = /^[A-Z][A-Z0-9]{8}$/i;
const NIF = /^[A-Z][0-9]{7}[A-Z0-9]$/i;
const PHONE = /^\+?[0-9 ]{9,15}$/;

type Text = FormControl<string>;
export type LegalForm = FormGroup<{
  legalEntityName: Text;
  eOfficeUrl: Text;
  ineCode: Text;
  dir3Code: Text;
  nif: Text;
  address: Text;
  generalEmail: Text;
  dpoEmail: Text;
  dpoPhone: Text;
  processor: Text;
  accessibilityEmail: Text;
  accessibilityUnit: Text;
  accessibilityComplaintUrl: Text;
  privacyMode: FormControl<LegalPageMode>;
  privacyUrl: Text;
  accessibilityMode: FormControl<LegalPageMode>;
  accessibilityUrl: Text;
  legalNoticeMode: FormControl<LegalPageMode>;
  legalNoticeUrl: Text;
}>;
export type LegalFieldKey = keyof LegalForm['controls'];
/** Os campos de texto (todos menos os modos). */
export type LegalTextKey = Exclude<LegalFieldKey, 'privacyMode' | 'accessibilityMode' | 'legalNoticeMode'>;
export type LegalTextForm = FormGroup<{ gl: Text; es: Text }>;
export type LegalTextsForm = FormGroup<Record<LegalPageKey, LegalTextForm>>;

/**
 * Os datos que usan os textos legais por defecto (maqueta 49): van enriba de todo, nun bloque
 * propio. Non son obrigatorios (o concello responde dos seus textos), pero se falta algún a
 * pestana «Legal» avisa.
 */
export const LEGAL_REQUIRED: readonly LegalTextKey[] = OPENING_LEGAL;
export const LEGAL_REQUIRED_ROWS: readonly (readonly LegalFieldKey[])[] = [
  ['legalEntityName', 'nif'],
  ['address'],
  ['generalEmail', 'dpoEmail'],
];

/** Os campos que usa só a páxina de Furar de cada páxina (van co modo «Páxina desta web»). */
export const FURAR_FIELDS: Readonly<Record<LegalPageKey, readonly LegalTextKey[]>> = {
  privacy: [],
  accessibility: ['accessibilityEmail', 'accessibilityUnit', 'accessibilityComplaintUrl'],
  legalNotice: ['dir3Code'],
};

/** Os que se usan tamén fóra desa páxina: sempre á vista, debaixo dunha liña. */
export const SHARED_FIELDS: Readonly<Record<LegalPageKey, readonly LegalFieldKey[]>> = {
  privacy: ['dpoPhone', 'processor'],
  accessibility: [],
  legalNotice: ['eOfficeUrl', 'ineCode'],
};

/** Os que faltan dos que usan os textos legais por defecto. */
export function missingLegal(legal: Partial<Record<LegalTextKey, string | undefined>>): LegalTextKey[] {
  return LEGAL_REQUIRED.filter((key) => !(legal[key] ?? '').trim());
}

/** Filas de campos de cada páxina: os da páxina desta web e os que se usan tamén fóra dela. */
export const PAGE_ROWS: Readonly<Record<LegalPageKey, { furar: LegalFieldKey[][]; shared: LegalFieldKey[][] }>> = {
  privacy: { furar: [], shared: [['dpoPhone'], ['processor']] },
  accessibility: { furar: [['accessibilityEmail'], ['accessibilityUnit'], ['accessibilityComplaintUrl']], shared: [] },
  legalNotice: { furar: [['dir3Code']], shared: [['eOfficeUrl', 'ineCode']] },
};

/** Todos os controis de cada pestana, para marcar a que ten erros. */
export function pageFields(page: LegalPageKey): readonly LegalFieldKey[] {
  const { mode, url } = LEGAL_PAGE_FIELDS[page];
  return [mode, url, ...FURAR_FIELDS[page], ...SHARED_FIELDS[page]];
}

/** As páxinas con algún erro á vista (tocado ou despois de «Gardar»): as súas pestanas levan «!». */
export function pagesWithErrors(legal: LegalForm, texts: LegalTextsForm, submitted: boolean): LegalPageKey[] {
  const shown = (control: { invalid: boolean; touched: boolean }) => control.invalid && (control.touched || submitted);
  return LEGAL_PAGES.filter(
    (page) =>
      pageFields(page).some((key) => shown(legal.controls[key])) ||
      shown(texts.controls[page].controls.gl) ||
      shown(texts.controls[page].controls.es),
  );
}

const trimmed = (value: unknown): string => (typeof value === 'string' ? value.trim() : '');

function rule(regex: RegExp, error: string, max: number = LEGAL_LIMITS.text): ValidatorFn {
  return (control) => {
    const value = trimmed(control.value);
    return value && (value.length > max || !regex.test(value)) ? { [error]: {} } : null;
  };
}

function size(max: number): ValidatorFn {
  return (control) => (trimmed(control.value).length > max ? { 'validation.size': { min: 0, max } } : null);
}

/** O enderezo da páxina propia: https sempre; obrigatorio só se o modo é «Páxina propia». */
function pageUrl(page: LegalPageKey): ValidatorFn {
  return (control: AbstractControl) => {
    const value = trimmed(control.value);
    if (!value) {
      return control.parent?.get(LEGAL_PAGE_FIELDS[page].mode)?.value === 'URL' ? { 'legal.url-required': {} } : null;
    }
    return value.length > LEGAL_LIMITS.url || !HTTPS.test(value) ? { 'legal.https': {} } : null;
  };
}

const text = (value: string | undefined, validators: ValidatorFn | ValidatorFn[]): Text =>
  new FormControl(value ?? '', { nonNullable: true, validators });

export function legalForm(legal: LegalDetailsResponse | undefined): LegalForm {
  const l = legal ?? { privacyMode: 'FURAR', accessibilityMode: 'FURAR', legalNoticeMode: 'FURAR' };
  const https = rule(HTTPS, 'legal.https', LEGAL_LIMITS.url);
  const email = rule(EMAIL, 'legal.email');
  const mode = (value: LegalPageMode | undefined) =>
    new FormControl<LegalPageMode>(value ?? 'FURAR', { nonNullable: true });
  const form: LegalForm = new FormGroup({
    legalEntityName: text(l.legalEntityName, size(LEGAL_LIMITS.text)),
    eOfficeUrl: text(l.eOfficeUrl, https),
    ineCode: text(l.ineCode, rule(INE, 'legal.ine')),
    dir3Code: text(l.dir3Code, rule(DIR3, 'legal.dir3')),
    nif: text(l.nif, rule(NIF, 'legal.nif')),
    address: text(l.address, size(LEGAL_LIMITS.text)),
    generalEmail: text(l.generalEmail, email),
    dpoEmail: text(l.dpoEmail, email),
    dpoPhone: text(l.dpoPhone, rule(PHONE, 'legal.phone')),
    processor: text(l.processor, size(LEGAL_LIMITS.text)),
    accessibilityEmail: text(l.accessibilityEmail, email),
    accessibilityUnit: text(l.accessibilityUnit, size(LEGAL_LIMITS.text)),
    accessibilityComplaintUrl: text(l.accessibilityComplaintUrl, https),
    privacyMode: mode(l.privacyMode),
    privacyUrl: text(l.privacyUrl, pageUrl('privacy')),
    accessibilityMode: mode(l.accessibilityMode),
    accessibilityUrl: text(l.accessibilityUrl, pageUrl('accessibility')),
    legalNoticeMode: mode(l.legalNoticeMode),
    legalNoticeUrl: text(l.legalNoticeUrl, pageUrl('legalNotice')),
  });
  for (const page of LEGAL_PAGES) {
    const { mode: modeKey, url: urlKey } = LEGAL_PAGE_FIELDS[page];
    let previous = form.controls[modeKey].value;
    form.controls[modeKey].valueChanges.subscribe((next) => {
      // O que se escribiu mal nun modo non segue ao seguinte: volve ao gardado (non se ve).
      const hidden: readonly LegalTextKey[] =
        previous === 'URL' ? [urlKey] : previous === 'FURAR' ? FURAR_FIELDS[page] : [];
      for (const key of hidden) {
        const control = form.controls[key];
        if (control.invalid) control.reset(String(l[key as keyof LegalDetailsResponse] ?? ''));
      }
      previous = next;
      form.controls[urlKey].updateValueAndValidity();
    });
  }
  return form;
}

export function legalTextsForm(texts: LegalTextsResponse | null): LegalTextsForm {
  const html = (value: string | undefined): Text => text(value, size(LEGAL_LIMITS.html));
  const page = (key: LegalPageKey): LegalTextForm =>
    new FormGroup({ gl: html(texts?.[key]?.gl), es: html(texts?.[key]?.es) });
  return new FormGroup({
    privacy: page('privacy'),
    accessibility: page('accessibility'),
    legalNotice: page('legalNotice'),
  });
}

export function legalBody(form: LegalForm): LegalDetailsRequest {
  const v = form.getRawValue();
  const t = (value: string) => value.trim();
  return {
    legalEntityName: t(v.legalEntityName),
    eOfficeUrl: t(v.eOfficeUrl),
    ineCode: t(v.ineCode),
    dir3Code: t(v.dir3Code).toUpperCase(),
    nif: t(v.nif).toUpperCase(),
    address: t(v.address),
    generalEmail: t(v.generalEmail),
    dpoEmail: t(v.dpoEmail),
    dpoPhone: t(v.dpoPhone),
    processor: t(v.processor),
    accessibilityEmail: t(v.accessibilityEmail),
    accessibilityUnit: t(v.accessibilityUnit),
    accessibilityComplaintUrl: t(v.accessibilityComplaintUrl),
    privacyMode: v.privacyMode,
    privacyUrl: t(v.privacyUrl),
    accessibilityMode: v.accessibilityMode,
    accessibilityUrl: t(v.accessibilityUrl),
    legalNoticeMode: v.legalNoticeMode,
    legalNoticeUrl: t(v.legalNoticeUrl),
  };
}

export function legalTextsBody(form: LegalTextsForm): LegalTextsRequest {
  const v = form.getRawValue();
  return { privacy: v.privacy, accessibility: v.accessibility, legalNotice: v.legalNotice };
}

/** Os datos legais do formulario tal como os le a páxina de Furar (vista previa e texto inicial). */
export function legalValues(form: LegalForm): Partial<LegalDetailsResponse> {
  return legalBody(form) as Partial<LegalDetailsResponse>;
}
