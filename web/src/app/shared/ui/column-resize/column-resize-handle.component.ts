// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, computed, input, signal } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import type { ColumnWidths } from './column-widths';

/** Px por cada frecha; con Maiúsculas, catro veces máis. */
const STEP = 16;

/**
 * Tirador do bordo dereito dunha cabeceira (dentro do `th`) para ensanchar ou estreitar a columna
 * (`table[furarColumnResize]`). Co rato ou o dedo, arrastrar; dobre clic, volve ao ancho de
 * partida. Co teclado é un separador enfocable: ← e → (con Maiúsculas, máis), Inicio (o mínimo) e
 * Fin (o máximo). Gárdase ao soltar ou coa tecla.
 */
@Component({
  selector: 'furar-column-resize',
  imports: [TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'f-column-resize' },
  template: `
    <span
      class="f-column-resize__handle"
      role="separator"
      tabindex="0"
      aria-orientation="vertical"
      [attr.aria-label]="'ui.columnResize.handle' | transloco: { label: label() }"
      [attr.aria-valuenow]="current()"
      [attr.aria-valuemin]="widths().min(column())"
      [attr.aria-valuemax]="widths().max"
      [attr.data-resize]="column()"
      [class.f-column-resize__handle--active]="dragging()"
      (pointerdown)="start($event)"
      (pointermove)="move($event)"
      (pointerup)="end($event)"
      (pointercancel)="end($event)"
      (keydown)="key($event)"
      (dblclick)="widths().reset(column())"
      (click)="$event.stopPropagation()"
    ></span>
  `,
})
export class ColumnResizeHandleComponent {
  readonly widths = input.required<ColumnWidths>();
  /** A clave da columna (a de `ResizableColumn`). */
  readonly column = input.required<string>();
  /** O nome visible da columna, para o nome do tirador («Ancho da columna Data»). */
  readonly label = input.required<string>();

  protected readonly current = computed(() => this.widths().width(this.column()) ?? 0);
  protected readonly dragging = signal(false);
  private startX = 0;
  private startWidth = 0;

  protected start(event: PointerEvent): void {
    if (event.button !== 0) return;
    event.preventDefault();
    const handle = event.currentTarget as HTMLElement;
    handle.setPointerCapture?.(event.pointerId);
    handle.focus();
    this.dragging.set(true);
    this.startX = event.clientX;
    this.startWidth = this.current();
  }

  protected move(event: PointerEvent): void {
    if (!this.dragging()) return;
    this.widths().set(this.column(), this.startWidth + event.clientX - this.startX, false);
  }

  protected end(event: PointerEvent): void {
    if (!this.dragging()) return;
    this.dragging.set(false);
    (event.currentTarget as HTMLElement).releasePointerCapture?.(event.pointerId);
    this.widths().save();
  }

  protected key(event: KeyboardEvent): void {
    const widths = this.widths();
    const column = this.column();
    const step = event.shiftKey ? STEP * 4 : STEP;
    const next: Record<string, number> = {
      ArrowLeft: this.current() - step,
      ArrowRight: this.current() + step,
      Home: widths.min(column),
      End: widths.max,
    };
    if (!(event.key in next)) return;
    event.preventDefault();
    widths.set(column, next[event.key]);
  }
}
