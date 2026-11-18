// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { ElementRef } from '@angular/core';
import { ChangeDetectionStrategy, Component, computed, input, model, signal, viewChildren } from '@angular/core';
import { IconComponent } from '../icon/icon.component';
import { BOTH_AXES, rovingIndex } from '../roving-index';
import { initials } from '../user-menu/initials';

/** Unha persoa que se pode escoller. */
export interface PersonOption {
  readonly id: string;
  /** O nome á vista («Eu», «Lúa Souto Pena»). */
  readonly label: string;
  /** Nome para as iniciais do círculo (por defecto, `label`). */
  readonly name?: string;
  /** Debaixo do nome, en gris: «Ao meu cargo · 2015». */
  readonly detail?: string;
  /** Non se pode escoller: o motivo vai debaixo do nome no canto do detalle. */
  readonly blocked?: string;
}

/**
 * Escoller unha persoa dunha lista (patrón «listbox» de selección única das prácticas ARIA): cada
 * fila co círculo das iniciais, o nome e o seu detalle e, á dereita, a marca de escollida. A que
 * non se pode escoller vai claramente atenuada, co motivo debaixo do nome, e non se marca (segue
 * na orde do teclado para que se lea o motivo). Unha soa parada de tabulador: as frechas, Inicio e
 * Fin moven o foco; Intro ou Espazo (ou o clic) escollen. Estilos en
 * `styles/components/person-picker.css`.
 *
 * ```html
 * <furar-person-picker label="Para quen" [options]="people" [(selected)]="who" />
 * ```
 */
@Component({
  selector: 'furar-person-picker',
  imports: [IconComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'contents' },
  template: `
    <ul class="f-person-picker" role="listbox" [attr.aria-label]="label()" [attr.aria-labelledby]="labelledBy()">
      @for (option of options(); track option.id; let i = $index) {
        <li
          #item
          class="f-person-picker__option"
          role="option"
          [attr.tabindex]="i === focusIndex() ? 0 : -1"
          [attr.aria-selected]="option.id === selected()"
          [attr.aria-disabled]="option.blocked ? 'true' : null"
          [attr.data-person]="option.id"
          (click)="choose(option, i)"
          (keydown)="onKeydown($event, option, i)"
        >
          <span class="f-person-picker__avatar" aria-hidden="true">{{ letters(option) }}</span>
          <span class="f-person-picker__who">
            <span class="f-person-picker__name">{{ option.label }}</span>
            @if (option.blocked || option.detail) {
              <small>{{ option.blocked || option.detail }}</small>
            }
          </span>
          @if (!option.blocked) {
            <span class="f-person-picker__mark" aria-hidden="true"><furar-icon name="check" /></span>
          }
        </li>
      }
    </ul>
  `,
})
export class PersonPickerComponent {
  /** Nome accesible da lista (se non hai `labelledBy`). */
  readonly label = input<string | null>(null);
  /** Id do rótulo visible da lista. */
  readonly labelledBy = input<string | null>(null);
  readonly options = input.required<readonly PersonOption[]>();
  /** O id escollido. */
  readonly selected = model<string | null>(null);
  /** Mentres se garda, non se cambia. */
  readonly disabled = input(false);

  private readonly items = viewChildren<ElementRef<HTMLElement>>('item');
  private readonly focused = signal<number | null>(null);

  /** A parada de tabulador: a última con foco, a escollida ou a primeira. */
  protected readonly focusIndex = computed(() => {
    const focused = this.focused();
    if (focused !== null && focused < this.options().length) return focused;
    const index = this.options().findIndex((option) => option.id === this.selected());
    return index >= 0 ? index : 0;
  });

  protected letters(option: PersonOption): string {
    return initials(option.name ?? option.label);
  }

  protected choose(option: PersonOption, index: number): void {
    this.focused.set(index);
    if (option.blocked || this.disabled()) return;
    this.selected.set(option.id);
  }

  protected onKeydown(event: KeyboardEvent, option: PersonOption, index: number): void {
    if (event.key === 'Enter' || event.key === ' ') {
      event.preventDefault();
      this.choose(option, index);
      return;
    }
    const next = rovingIndex(event.key, index, this.options().length, BOTH_AXES);
    if (next === null) return;
    event.preventDefault();
    this.focused.set(next);
    this.items()[next]?.nativeElement.focus();
  }
}
