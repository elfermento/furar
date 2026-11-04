// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, computed, input, signal } from '@angular/core';
import type { IconName } from './icons';
import { SYMBOLS } from './material-symbols';

/** Camiños de `lazy-symbols.ts`, fóra da carga inicial: chegan cunha páxina ou co *chunk* diferido. */
const registered = signal<Readonly<Partial<Record<IconName, string>>>>({});

/** Rexistra iconas dunha páxina cargada á parte; chámase ao cargar o seu módulo, antes de pintar. */
export function registerSymbols(symbols: Readonly<Partial<Record<IconName, string>>>): void {
  registered.update((current) => ({ ...current, ...symbols }));
}

let loading: Promise<void> | null = null;

/**
 * Carga `lazy-symbols.ts` (unha vez) cando se pide unha icona que non está no marco; mentres, a
 * icona ocupa o seu sitio baleira. Se falla (sen rede), tenta de novo coa seguinte icona.
 */
export function loadLazySymbols(): Promise<void> {
  loading ??= import('./lazy-symbols').then(
    (m) => registerSymbols({ ...m.LAZY_SYMBOLS, ...m.AMENITY_SYMBOLS }),
    () => {
      loading = null;
    },
  );
  return loading;
}

/**
 * Icona SVG en currentColor (Material Symbols de liña, a única familia). Decorativa por defecto
 * (aria-hidden); con `label` pasa a role="img" co nome (o texto vén traducido de quen chama).
 */
@Component({
  selector: 'furar-icon',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    class: 'f-icon',
    '[attr.data-size]': 'size()',
    '[attr.aria-hidden]': 'label() ? null : "true"',
    '[attr.role]': 'label() ? "img" : null',
    '[attr.aria-label]': 'label() || null',
  },
  template: `
    <svg viewBox="0 -960 960 960" focusable="false" aria-hidden="true">
      <path [attr.d]="path()" />
    </svg>
  `,
})
export class IconComponent {
  readonly name = input.required<IconName>();
  readonly label = input('');
  readonly size = input<20 | 24>(20);
  /** Un só camiño con recheo (Material Symbols). */
  protected readonly path = computed(() => {
    const name = this.name();
    if (name in SYMBOLS) return SYMBOLS[name as keyof typeof SYMBOLS];
    const path = registered()[name];
    if (path === undefined) void loadLazySymbols();
    return path ?? '';
  });
}
