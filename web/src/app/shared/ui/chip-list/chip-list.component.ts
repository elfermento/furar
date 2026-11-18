// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { ElementRef } from '@angular/core';
import {
  ChangeDetectionStrategy,
  Component,
  Injector,
  afterNextRender,
  booleanAttribute,
  inject,
  input,
  output,
  viewChild,
} from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { IconComponent } from '../icon/icon.component';

/** Un chip da lista: `key` estable para o `track`; `label` xa traducido. */
export interface ChipItem {
  readonly key: string;
  readonly label: string;
  /** Cun fino bordo na liña, para distinguir uns poucos (p. ex. os deportes do concello). */
  readonly outline?: boolean;
}

/** Algo que pode recibir o foco (un `furar-combobox`, un `HTMLElement`). */
export interface Focusable {
  focus(): void;
}

/**
 * Lista de chips eliminables (docs/deseno.md §8): o escollido nun campo de varios valores, debaixo
 * do campo. Cada chip ten o aspecto de `furar-tag` `soft` (fondo `tint`, letra na tinta) e un «×»
 * de 40 px co nome «Quitar …». Non cambia o valor: emite `removed` co índice e a feature quita.
 *
 * Teclado: Tab percorre os «×»; Supr ou Retroceso nun «×» quítao; ← e → móvense entre eles (→ no
 * último volve a `fallbackFocus`). Tras quitar, o foco vai ao «×» seguinte (ao anterior se era o
 * último) ou, se non queda ningún, a `fallbackFocus` (o campo). `status` é o texto dun
 * `role="status"` educado, sempre presente, no que a feature anuncia cada alta e baixa.
 */
@Component({
  selector: 'furar-chip-list',
  imports: [IconComponent, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'f-chip-list-host' },
  template: `
    @if (items().length) {
      <ul class="f-chip-list" #list [attr.aria-label]="label()">
        @for (item of items(); track item.key; let i = $index) {
          <li class="f-chip" [attr.data-outline]="item.outline || null">
            <span class="f-chip__label">{{ item.label }}</span>
            <button
              type="button"
              class="f-chip__remove"
              [attr.data-chip-remove]="i"
              [disabled]="disabled()"
              [attr.aria-label]="'ui.chipList.remove' | transloco: { name: item.label }"
              (click)="remove(i)"
              (keydown)="onKeydown($event, i)"
            >
              <furar-icon name="close" />
            </button>
          </li>
        }
      </ul>
    }
    <span class="cdk-visually-hidden" role="status">{{ status() }}</span>
  `,
})
export class ChipListComponent {
  private readonly injector = inject(Injector);
  private readonly list = viewChild<ElementRef<HTMLUListElement>>('list');

  readonly items = input.required<readonly ChipItem[]>();
  /** Nome da lista («Deportes escollidos»), xa traducido. */
  readonly label = input.required<string>();
  /** Anuncio educado da última alta ou baixa, xa traducido. */
  readonly status = input('');
  /** Onde vai o foco ao quitar o último chip (e con → no último «×»): o campo. */
  readonly fallbackFocus = input<Focusable | null>(null);
  readonly disabled = input(false, { transform: booleanAttribute });

  /** Pediuse quitar o chip deste índice. */
  readonly removed = output<number>();

  protected remove(index: number): void {
    this.removed.emit(index);
    afterNextRender(() => this.focusAt(index), { injector: this.injector });
  }

  protected onKeydown(event: KeyboardEvent, index: number): void {
    if (event.altKey || event.ctrlKey || event.metaKey || event.shiftKey) return;
    switch (event.key) {
      case 'Delete':
      case 'Backspace':
        event.preventDefault();
        this.remove(index);
        break;
      case 'ArrowLeft':
        event.preventDefault();
        if (index > 0) this.buttons()[index - 1]?.focus();
        break;
      case 'ArrowRight': {
        event.preventDefault();
        const next = this.buttons()[index + 1];
        if (next) next.focus();
        else this.fallbackFocus()?.focus();
        break;
      }
    }
  }

  /** O «×» que queda neste sitio (ou o anterior, se era o último); sen ningún, o campo. */
  private focusAt(index: number): void {
    const buttons = this.buttons();
    const target = buttons[Math.min(index, buttons.length - 1)];
    if (target) target.focus();
    else this.fallbackFocus()?.focus();
  }

  private buttons(): HTMLButtonElement[] {
    return [...(this.list()?.nativeElement.querySelectorAll<HTMLButtonElement>('[data-chip-remove]') ?? [])];
  }
}
