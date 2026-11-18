// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { DestroyRef, Signal } from '@angular/core';
import { signal } from '@angular/core';
import type { UserMenuItem, UserMenuSwitcher } from '../../shared/ui/user-menu/user-menu.component';
import type { Language } from '../i18n/languages';
import { LANGUAGES } from '../i18n/languages';

/** Por baixo deste ancho, a barra do móbil (o mesmo corte ca site.css, 640 px). */
export const COMPACT_QUERY = '(max-width: 639px)';

/**
 * «Idioma ›» no menú da persoa (en todos os anchos), co idioma actual á dereita. Cada opción, co
 * nome no propio idioma e o seu atributo `lang` (WCAG 3.1.2).
 */
export function languageMenu(current: Language, t: (key: string) => string): UserMenuSwitcher {
  return {
    label: t('lang.label'),
    current: t('lang.endonym.' + current),
    icon: 'globe',
    options: LANGUAGES.map((lang) => ({
      id: lang,
      label: t('lang.endonym.' + lang),
      lang,
      current: lang === current,
    })),
  };
}

/** Sen sesión: «Entrar» (o idioma vai despois, tras unha liña). */
export function guestMenuItems(t: (key: string) => string): readonly UserMenuItem[] {
  return [{ id: 'login', label: t('session.login'), icon: 'user' }];
}

/** Se a media query se cumpre agora, e ao momento cando cambia (sen `matchMedia`, nunca). */
export function mediaMatches(view: Window | null, query: string, destroyRef: DestroyRef): Signal<boolean> {
  const list = view?.matchMedia?.(query);
  const matches = signal(list?.matches ?? false);
  if (list) {
    const onChange = () => matches.set(list.matches);
    list.addEventListener?.('change', onChange);
    destroyRef.onDestroy(() => list.removeEventListener?.('change', onChange));
  }
  return matches.asReadonly();
}
