// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { LegalPageKey } from '../../../core/municipality/legal-pages';
import type { LegalTextKey } from '../legal-form';

type Translate = (key: string, params?: Record<string, unknown>) => string;

const list = (lang: string, names: string[]) => new Intl.ListFormat(lang, { type: 'conjunction' }).format(names);

/** «Faltan datos que usan as páxinas legais por defecto: NIF e domicilio. Sen eles, …» (ou nada). */
export function missingMessage(missing: readonly LegalTextKey[], t: Translate, lang: string): string {
  if (!missing.length) return '';
  const names = missing.map((key) => t('settings.legal.missingName.' + key));
  return t(missing.length === 1 ? 'settings.legal.missingOne' : 'settings.legal.missing', {
    list: list(lang, names),
  });
}

/** «Hai campos por revisar en os datos que usan os textos legais e Privacidade.» (ou nada). */
export function errorsMessage(
  pages: readonly LegalPageKey[],
  requiredWrong: boolean,
  t: Translate,
  lang: string,
): string {
  const names = pages.map((page) => t('settings.legal.tab.' + page));
  if (requiredWrong) names.unshift(t('settings.legal.required.inList'));
  return names.length ? t('settings.legal.errorsIn', { pages: list(lang, names) }) : '';
}
