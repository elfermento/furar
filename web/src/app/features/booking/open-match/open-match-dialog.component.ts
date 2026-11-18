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
import { toSignal } from '@angular/core/rxjs-interop';
import { ReactiveFormsModule } from '@angular/forms';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import type { ApiProblem } from '../../../core/errors/api-problem';
import { asProblem } from '../../../core/errors/problem.interceptor';
import { OPEN_MATCH_LEVELS } from '../../../shared/open-matches/open-match-format';
import type { MyOpenMatchResponse } from '../../../shared/open-matches/open-matches.store';
import { OpenMatches } from '../../../shared/open-matches/open-matches.store';
import { ButtonDirective } from '../../../shared/ui/button/button.directive';
import { DialogComponent } from '../../../shared/ui/dialog/dialog.component';
import { FieldGroupComponent } from '../../../shared/ui/field-group/field-group.component';
import { describedBy, fieldError } from '../../../shared/ui/field-errors';
import { FieldComponent } from '../../../shared/ui/field.component';
import { focusFirstError } from '../../../shared/ui/focus-error/focus-error';
import { InputDirective } from '../../../shared/ui/input/input.directive';
import { ProblemAlertComponent } from '../../../shared/ui/problem-alert.component';
import type { SelectOption } from '../../../shared/ui/select/select-option';
import { SelectComponent } from '../../../shared/ui/select/select.component';
import { SwitchComponent } from '../../../shared/ui/switch/switch.component';
import type { LevelChoice } from './open-match-form';
import { DESCRIPTION_MAX, TITLE_MAX, openMatchBody, openMatchForm } from './open-match-form';

/** O que abre o diálogo: a reserva, o prazo do concello e, ao editar, a partida que xa hai. */
export interface OpenMatchDialogData {
  readonly slug: string;
  readonly bookingId: string;
  /** Prazas da reserva: o «Xa somos» por defecto. */
  readonly seats: number;
  /** Cabeceira: «Fútbol sala · venres 20 nov. · 20:00–21:30». */
  readonly subheading: string;
  /** «Cancelar se non hai mínimo» do concello (horas antes), para a axuda do mínimo. */
  readonly cancelHours: number;
  readonly existing: MyOpenMatchResponse | null;
}

/**
 * «Buscar xente para xogar» desde o detalle dunha reserva propia (ADR-0040):
 * nivel, título e descrición (texto plano), e na mesma cápsula a fila «Xa somos · Mínimo ·
 * Máximo»; debaixo, a fila solta «Con aprobación» (apagada por defecto) e o aviso do que se
 * publica, onde e ata cando (informativo, sen nada que marcar: base 6.1.e). «Publicar» abre;
 * editando, «Gardar» (coa versión que se viu). Ben, pecha coa partida; mal, o erro queda na capa e
 * o foco vai ao primeiro campo con erro.
 */
@Component({
  selector: 'furar-open-match-dialog',
  imports: [
    ButtonDirective,
    DialogComponent,
    FieldComponent,
    FieldGroupComponent,
    InputDirective,
    ProblemAlertComponent,
    ReactiveFormsModule,
    SelectComponent,
    SwitchComponent,
    TranslocoPipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './open-match-dialog.component.html',
})
export class OpenMatchDialogComponent {
  protected readonly data = inject<OpenMatchDialogData>(DIALOG_DATA);
  protected readonly ref = inject<DialogRef<MyOpenMatchResponse>>(DialogRef);
  private readonly store = inject(OpenMatches);
  private readonly transloco = inject(TranslocoService);
  private readonly injector = inject(Injector);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly langChanges = toSignal(this.transloco.langChanges$);

  protected readonly editing = this.data.existing !== null;
  protected readonly form = openMatchForm(this.data.existing, this.data.seats);
  protected readonly titleMax = TITLE_MAX;
  protected readonly descriptionMax = DESCRIPTION_MAX;
  protected readonly describedBy = describedBy;
  protected readonly busy = signal(false);
  protected readonly submitted = signal(false);
  protected readonly problem = signal<ApiProblem | null>(null);
  private readonly formEvents = toSignal(this.form.events);

  protected readonly levels = computed<SelectOption<LevelChoice>[]>(() => {
    this.langChanges();
    return (['NONE', ...OPEN_MATCH_LEVELS] as const).map((value) => ({
      value,
      label: this.transloco.translate(`openMatch.dialog.levels.${value}`),
    }));
  });
  protected readonly errors = computed(() => {
    this.formEvents();
    const c = this.form.controls;
    const shown = this.submitted();
    return {
      title: fieldError(c.title, shown),
      description: fieldError(c.description, shown),
      knownCount: fieldError(c.knownCount, shown),
      minimum: fieldError(c.minimum, shown),
      maximum: fieldError(c.maximum, shown),
    };
  });
  protected readonly saveKey = computed(() => {
    if (this.busy()) return this.editing ? 'common.saving' : 'openMatch.dialog.publishing';
    return this.editing ? 'common.save' : 'openMatch.dialog.publish';
  });

  protected async submit(event: Event): Promise<void> {
    event.preventDefault();
    if (this.busy()) return;
    this.submitted.set(true);
    this.problem.set(null);
    this.form.updateValueAndValidity();
    this.form.markAllAsTouched();
    if (this.form.invalid) {
      afterNextRender(() => focusFirstError(this.host.nativeElement), { injector: this.injector });
      return;
    }
    this.busy.set(true);
    try {
      const saved = await this.store.save(
        this.data.slug,
        this.data.bookingId,
        openMatchBody(this.form),
        this.data.existing?.version,
      );
      this.ref.close(saved);
    } catch (error) {
      this.problem.set(asProblem(error));
    } finally {
      this.busy.set(false);
    }
  }
}
