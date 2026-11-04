// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { Directive, input, model } from '@angular/core';

/** Ton dun chip de filtro: o dun estado da paleta (docs/deseno.md §3.1). Sen ton, neutro. */
export type FilterChipTone = 'success' | 'warn' | 'danger' | 'info' | 'muted';

/**
 * Chip de filtro (docs/deseno.md P1-9, excepción do 2026-10-26): un `<button>` con `aria-pressed`
 * que marca ou desmarca un filtro. Non é unha etiqueta: é a única peza que pode levar o fondo
 * pálido do ton coa letra no ton (sen marcar); marcado, recheo do ton con letra branca. Neutro
 * (sen ton), branco e, marcado, bordo máis groso en `primary`: nunca negro nin ✓.
 *
 * Uso: `<button type="button" furarFilterChip="warn" [(pressed)]="pending">Pendente</button>`. O clic cambia
 * `pressed`; quen o usa decide que filtrar. Estilos en `styles/reservas.css`: a ruta leva
 * `bookingStylesResolver`.
 */
@Directive({
  selector: 'button[furarFilterChip]',
  host: {
    class: 'f-filter-chip',
    '[attr.aria-pressed]': 'pressed()',
    '[attr.data-tone]': 'tone()',
    '(click)': 'toggle()',
  },
})
export class FilterChipDirective {
  readonly tone = input<FilterChipTone | null, FilterChipTone | '' | null>(null, {
    alias: 'furarFilterChip',
    transform: (value: FilterChipTone | '' | null) => value || null,
  });
  /** Marcado (`aria-pressed`); con `[(pressed)]` vai e volve. */
  readonly pressed = model(false);
  protected toggle(): void {
    this.pressed.update((value) => !value);
  }
}
