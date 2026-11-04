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
} from '@angular/core';
import { ReactiveFormsModule } from '@angular/forms';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import type { ApiProblem } from '../../../core/errors/api-problem';
import { LanguageService } from '../../../core/i18n/language.service';
import { ButtonDirective } from '../../../shared/ui/button/button.directive';
import { DateFieldComponent } from '../../../shared/ui/date-field/date-field.component';
import { DeleteButtonComponent } from '../../../shared/ui/delete-button/delete-button.component';
import { DialogComponent } from '../../../shared/ui/dialog/dialog.component';
import { focusFirstError } from '../../../shared/ui/edit-section/section-errors';
import { describedBy, fieldError } from '../../../shared/ui/field-errors';
import { FieldComponent } from '../../../shared/ui/field.component';
import { InputDirective } from '../../../shared/ui/input/input.directive';
import { ProblemAlertComponent } from '../../../shared/ui/problem-alert.component';
import { TimePickerComponent } from '../../../shared/ui/time-picker/time-picker.component';
import type { BlockSlot } from './block-form';
import { DAY_END, MINUTE_STEP, REASON_MAX, applyBlockErrors, blockBody, blockForm } from './block-form';
import { blockWhen } from './block-format';
import type { Block, BlockBody } from './blocks.store';
import { RequiredMarkComponent, RequiredNoteComponent } from '../../../shared/ui/required/required.component';

export interface BlockDialogData {
  /** O bloqueo tal como se ve agora (cos cambios sen gardar); sen el, un novo. */
  readonly block: Block | null;
  /** Os outros bloqueos: o tramo non pode pisalos. */
  readonly others: readonly BlockSlot[];
  readonly problem: ApiProblem | null;
}

export type BlockDialogResult = { readonly kind: 'apply'; readonly body: BlockBody } | { readonly kind: 'remove' };

let nextId = 0;

/**
 * Modal dun bloqueo no modo edición da ficha (D1-24; especificación, bloque 2, «Bloqueos»): data,
 * desde e ata (ata as 24:00) e o motivo. Valida igual ca a API (data obrigatoria, fin despois do
 * inicio, sen pisar outro bloqueo, motivo de ata 200). «Aceptar» só deixa o cambio na ficha; vai á
 * API cando a barra garda (se deixa reservas fóra, o modal de D1-19).
 */
@Component({
  selector: 'furar-facility-block-dialog',
  imports: [
    RequiredMarkComponent,
    RequiredNoteComponent,
    ButtonDirective,
    DateFieldComponent,
    DeleteButtonComponent,
    DialogComponent,
    FieldComponent,
    InputDirective,
    ProblemAlertComponent,
    ReactiveFormsModule,
    TimePickerComponent,
    TranslocoPipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './block-dialog.component.html',
})
export class BlockDialogComponent {
  protected readonly data = inject<BlockDialogData>(DIALOG_DATA);
  protected readonly ref = inject<DialogRef<BlockDialogResult>>(DialogRef);
  private readonly transloco = inject(TranslocoService);
  private readonly language = inject(LanguageService);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly injector = inject(Injector);

  protected readonly id = `furar-block-${nextId++}`;
  protected readonly reasonMax = REASON_MAX;
  protected readonly lastStart = DAY_END - MINUTE_STEP;
  protected readonly describedBy = describedBy;
  protected readonly form = blockForm(this.data.block, () => this.data.others);
  protected readonly problem = signal<ApiProblem | null>(null);
  private readonly formEvents = signal(0);

  protected readonly errors = computed(() => {
    this.formEvents();
    const c = this.form.controls;
    return {
      date: fieldError(c.date, false),
      period: fieldError(c.startMinute, false) ?? fieldError(c.endMinute, false) ?? fieldError(this.form, false),
      reason: fieldError(c.reason, false),
    };
  });
  protected readonly when = computed(() => (this.data.block ? blockWhen(this.data.block, this.language.lang()) : ''));
  protected readonly heading = computed(() =>
    this.data.block
      ? this.transloco.translate('facility.blocks.edit', { when: this.when() })
      : this.transloco.translate('facility.blocks.addTitle'),
  );

  constructor() {
    this.form.events.subscribe(() => this.formEvents.update((n) => n + 1));
    // Os erros da API van aos campos xa ligados (ligalos revalida e borraría os postos antes).
    const problem = this.data.problem;
    if (problem) {
      afterNextRender(
        () => {
          if (!applyBlockErrors(this.form, problem)) this.problem.set(problem);
          this.formEvents.update((n) => n + 1);
          focusFirstError(this.host.nativeElement);
        },
        { injector: this.injector },
      );
    }
  }

  protected submit(event: Event): void {
    event.preventDefault();
    this.problem.set(null);
    this.form.updateValueAndValidity();
    this.form.markAllAsTouched();
    this.formEvents.update((n) => n + 1);
    if (this.form.invalid) return this.focusError();
    this.ref.close({ kind: 'apply', body: blockBody(this.form) });
  }

  protected remove(): void {
    this.ref.close({ kind: 'remove' });
  }

  private focusError(): void {
    afterNextRender(() => focusFirstError(this.host.nativeElement), { injector: this.injector });
  }
}
