// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  Injector,
  afterNextRender,
  computed,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { ReactiveFormsModule } from '@angular/forms';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import type { ApiProblem } from '../../../core/errors/api-problem';
import { ButtonDirective } from '../../../shared/ui/button/button.directive';
import { DeleteButtonComponent } from '../../../shared/ui/delete-button/delete-button.component';
import { DialogComponent } from '../../../shared/ui/dialog/dialog.component';
import { focusFirstError } from '../../../shared/ui/edit-section/section-errors';
import { describedBy, fieldError } from '../../../shared/ui/field-errors';
import { FieldComponent } from '../../../shared/ui/field.component';
import { IconComponent } from '../../../shared/ui/icon/icon.component';
import { InputDirective } from '../../../shared/ui/input/input.directive';
import { ProblemAlertComponent } from '../../../shared/ui/problem-alert.component';
import {
  SegmentedOptionDirective,
  SegmentedRadioDirective,
} from '../../../shared/ui/segmented/segmented-radio.directive';
import { SegmentedDirective } from '../../../shared/ui/segmented/segmented.directive';
import { RENTAL_MODES, SLOT_MINUTES, USE_LIMITS, applyUseErrors, partForm, useBody, useForm } from './use-form';
import { UseRulesComponent } from './use-rules.component';
import { PRICE_KINDS } from './use-terms';
import { UseWhenComponent } from './use-when.component';
import type { Use, UseBody } from './uses.store';
import { RequiredMarkComponent, RequiredNoteComponent } from '../../../shared/ui/required/required.component';

export interface UseDialogData {
  /** O uso tal como se ve agora (cos cambios sen gardar); sen el, un novo. */
  readonly use: Use | null;
  /** O que respondeu a API ao intentar gardalo, para poñelo nos campos. */
  readonly problem: ApiProblem | null;
  /** Nome da instalación, en gris debaixo do título (maqueta 21). */
  readonly facilityName?: string;
}

/** «Aceptar» co corpo, «Eliminar» ou pechado sen máis (`undefined`). */
export type UseDialogResult = { readonly kind: 'apply'; readonly body: UseBody } | { readonly kind: 'remove' };

let nextId = 0;

/**
 * Modal dun uso no modo edición da ficha (D1-22 e D2-11; maqueta 21): nome, «Alúgase», partes ou
 * prazas segundo o modo e «Cando» (fila-resumo cun panel: datas, días da semana e «Bloquea os
 * demais usos»); «Duración e prezo» (duración, Sen prezo · Gratis · De pago e o importe) e
 * «Condicións da reserva» en acordeón. Valida igual ca a API e «Aceptar» só deixa o cambio na ficha: vai á API
 * cando a barra garda. Se ao gardar a API o rexeitou, os erros saen no seu campo.
 */
@Component({
  selector: 'furar-facility-use-dialog',
  imports: [
    RequiredMarkComponent,
    RequiredNoteComponent,
    ButtonDirective,
    DeleteButtonComponent,
    DialogComponent,
    FieldComponent,
    IconComponent,
    InputDirective,
    ProblemAlertComponent,
    ReactiveFormsModule,
    SegmentedDirective,
    SegmentedOptionDirective,
    SegmentedRadioDirective,
    TranslocoPipe,
    UseRulesComponent,
    UseWhenComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './use-dialog.component.html',
})
export class UseDialogComponent {
  protected readonly data = inject<UseDialogData>(DIALOG_DATA);
  protected readonly ref = inject<DialogRef<UseDialogResult>>(DialogRef);
  private readonly transloco = inject(TranslocoService);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly injector = inject(Injector);
  private readonly when = viewChild.required(UseWhenComponent);
  private readonly rules = viewChild.required(UseRulesComponent);

  protected readonly id = `furar-use-${nextId++}`;
  protected readonly modes = RENTAL_MODES;
  protected readonly slots = SLOT_MINUTES;
  protected readonly priceKinds = PRICE_KINDS;
  protected readonly limits = USE_LIMITS;
  protected readonly describedBy = describedBy;
  protected readonly form = useForm(this.data.use ?? undefined);
  protected readonly problem = signal<ApiProblem | null>(null);
  private readonly formEvents = signal(0);

  protected readonly errors = computed(() => {
    this.formEvents();
    const c = this.form.controls;
    return {
      name: fieldError(c.name, false),
      capacity: fieldError(c.capacity, false),
      parts: fieldError(c.parts, false),
      partNames: c.parts.controls.map((part) => fieldError(part.controls.name, false)),
      amount: fieldError(c.price.controls.amountCents, false),
    };
  });
  protected readonly value = computed(() => {
    this.formEvents();
    return this.form.getRawValue();
  });
  protected readonly heading = computed(() =>
    this.transloco.translate(this.data.use ? 'facility.uses.edit' : 'facility.uses.add'),
  );

  constructor() {
    this.form.events.subscribe(() => this.formEvents.update((n) => n + 1));
    // Os erros da API van aos campos xa ligados (ligalos revalida e borraría os postos antes).
    const problem = this.data.problem;
    if (problem) {
      afterNextRender(
        () => {
          if (!applyUseErrors(this.form, problem)) this.problem.set(problem);
          this.formEvents.update((n) => n + 1);
          this.focusError();
        },
        { injector: this.injector },
      );
    }
  }

  protected addPart(): void {
    this.form.controls.parts.push(partForm());
    this.form.controls.parts.markAsDirty();
    this.focusLast('[data-part] input');
  }

  protected removePart(index: number): void {
    this.form.controls.parts.removeAt(index);
    this.form.controls.parts.markAsDirty();
    this.focusLast('[data-part] input');
  }

  protected submit(event: Event): void {
    event.preventDefault();
    this.problem.set(null);
    this.form.markAllAsTouched();
    this.formEvents.update((n) => n + 1);
    if (this.form.invalid) return this.focusError();
    this.ref.close({ kind: 'apply', body: useBody(this.form) });
  }

  protected remove(): void {
    this.ref.close({ kind: 'remove' });
  }

  /**
   * Foco no primeiro erro á vista; se non hai, os do «Cando» (abre o seu panel) ou os das normas
   * (abre a norma), que están pechados.
   */
  private focusError(): void {
    afterNextRender(
      () => {
        const host = this.host.nativeElement;
        if (host.querySelector('[aria-invalid="true"]') && focusFirstError(host)) return;
        if (this.when().invalid()) return this.when().open();
        if (this.rules().revealErrors()) return;
        focusFirstError(host);
      },
      { injector: this.injector },
    );
  }

  private focusLast(selector: string): void {
    afterNextRender(() => [...this.host.nativeElement.querySelectorAll<HTMLElement>(selector)].pop()?.focus(), {
      injector: this.injector,
    });
  }
}
