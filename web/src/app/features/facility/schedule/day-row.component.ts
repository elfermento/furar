// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  Injector,
  afterNextRender,
  booleanAttribute,
  computed,
  inject,
  input,
  output,
} from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { ReactiveFormsModule } from '@angular/forms';
import { TranslocoPipe } from '@jsverse/transloco';
import { startWith, switchMap } from 'rxjs';
import { LanguageService } from '../../../core/i18n/language.service';
import { ButtonDirective } from '../../../shared/ui/button/button.directive';
import { IconComponent } from '../../../shared/ui/icon/icon.component';
import { SwitchComponent } from '../../../shared/ui/switch/switch.component';
import { DeleteButtonComponent } from '../../../shared/ui/delete-button/delete-button.component';
import { TimePickerComponent } from '../../../shared/ui/time-picker/time-picker.component';
import { intervalsText } from './week-format';
import type { DayForm } from './week-form';
import { INTERVALS_MAX, addInterval, dayError, dayIntervals, removeInterval } from './week-form';

/** Por que pide pecharse a fila: un clic fóra dela ou Esc dentro. */
export type DayRowClose = 'outside' | 'escape';

/**
 * Un día da semana (D1-23a; especificación, bloque 2, «Semana»; maqueta `02-ficha-instalacion`).
 * En repouso é un botón co texto resolto («Luns 8:00 – 23:00», «Pechado»); en edición, interruptor
 * aberto/pechado, tramos «desde – ata» con `furar-time-picker`, «Eliminar» e «+ Tramo», e o
 * erro do día debaixo. Quen o usa decide se se abre ou pecha: a fila só avisa cando se pica fóra
 * dela (un clic, non `focusout`: tabular ata «+ Tramo» ou máis alá non a pecha) ou se preme Esc.
 */
@Component({
  selector: 'furar-facility-day-row',
  imports: [
    ButtonDirective,
    IconComponent,
    ReactiveFormsModule,
    SwitchComponent,
    DeleteButtonComponent,
    TimePickerComponent,
    TranslocoPipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    class: 'block',
    '(document:click)': 'onDocumentClick($event)',
    '(keydown.escape)': 'onEscape($event)',
  },
  templateUrl: './day-row.component.html',
})
export class DayRowComponent {
  readonly day = input.required<DayForm>();
  /** Posición na semana (0 = luns, 7 = festivos). */
  readonly index = input.required<number>();
  /** «Luns», «Festivos»… */
  readonly name = input.required<string>();
  readonly editing = input(false, { transform: booleanAttribute });
  /** Prefixo dos `id` (un por semana). */
  readonly idPrefix = input.required<string>();

  readonly opened = output();
  readonly closeRequested = output<DayRowClose>();

  private readonly language = inject(LanguageService);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly injector = inject(Injector);
  protected readonly max = INTERVALS_MAX;
  private readonly events = toSignal(toObservable(this.day).pipe(switchMap((day) => day.events.pipe(startWith(null)))));

  protected readonly value = computed(() => {
    this.events();
    return this.day().getRawValue();
  });
  protected readonly hours = computed(() => {
    this.events();
    return intervalsText(dayIntervals(this.day()), this.language.lang());
  });
  protected readonly problem = computed(() => {
    this.events();
    return dayError(this.day());
  });
  protected readonly errorId = computed(() => `${this.idPrefix()}-day-${this.index()}-error`);

  protected add(): void {
    addInterval(this.day());
    this.focus(() =>
      [...this.host.nativeElement.querySelectorAll<HTMLElement>('[data-interval]')].at(-1)?.querySelector('button'),
    );
  }

  /** Quita o tramo e deixa o foco no anterior (ou en «+ Tramo» se non queda ningún). */
  protected remove(index: number): void {
    removeInterval(this.day(), index);
    this.focus(() => {
      const rows = this.host.nativeElement.querySelectorAll<HTMLElement>('[data-interval]');
      return (
        rows.item(Math.max(0, index - 1))?.querySelector<HTMLElement>('button') ??
        this.host.nativeElement.querySelector<HTMLElement>('[data-interval-add]')
      );
    });
  }

  protected onDocumentClick(event: MouseEvent): void {
    if (!this.editing()) return;
    // O camiño quedou fixado ao picar: vale aínda que o elemento picado xa non estea no DOM.
    const path = event.composedPath();
    const inOverlay = path.some((node) => node instanceof Element && node.matches('.cdk-overlay-container'));
    if (!path.includes(this.host.nativeElement) && !inOverlay) this.closeRequested.emit('outside');
  }

  protected onEscape(event: Event): void {
    // Se xa o atendeu un control da fila (a lista de horas), só pecha ese control.
    if (!this.editing() || event.defaultPrevented) return;
    event.stopPropagation();
    this.closeRequested.emit('escape');
  }

  private focus(target: () => HTMLElement | null | undefined): void {
    afterNextRender(() => target()?.focus(), { injector: this.injector });
  }
}
