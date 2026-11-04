// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { DoCheck } from '@angular/core';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  Injector,
  afterNextRender,
  contentChildren,
  inject,
  input,
  output,
  signal,
  viewChild,
} from '@angular/core';
import type { AbstractControl, FormGroup } from '@angular/forms';
import { FormGroupDirective } from '@angular/forms';
import { TranslocoPipe } from '@jsverse/transloco';
import type { Observable, Subscription } from 'rxjs';
import { firstValueFrom, isObservable } from 'rxjs';
import type { ApiProblem } from '../../../core/errors/api-problem';
import { UnsavedChangesService } from '../../../core/unsaved-changes/unsaved-changes.service';
import { ButtonDirective } from '../button/button.directive';
import { EditSession } from '../edit-session/edit-session';
import { IconComponent } from '../icon/icon.component';
import { ProblemAlertComponent } from '../problem-alert.component';
import { EditFieldComponent } from './edit-field.component';
import { applyFieldErrors, asProblem, focusFirstError } from './section-errors';

/** Canto se ve «Gardado» tras gardar (P21: uns segundos, sen aviso flotante). */
export const SAVED_NOTICE_MS = 4000;

let nextId = 0;

/**
 * Sección editable no sitio (D1-05b; docs/deseno.md §8, substitúe a `furar-summary-card`): un
 * `<section>` co seu título e un `<form>` co `FormGroup` tipado que pon a feature
 * (`<furar-edit-section [formGroup]="form" [save]="gardar">`). **Non decide o control**: o contido
 * é da feature, con `furar-edit-field` para os valores que en repouso son un botón.
 *
 * Mentres o formulario está sucio amosa «Cambios sen gardar» con «Cancelar» e «Gardar».
 * Gardar: se hai erros, abre os campos e leva o foco ao primeiro; se non, chama a `save` e, se
 * vai ben, deixa o formulario limpo, pecha os campos, anuncia «Gardado» (`role="status"`) e leva o
 * foco ao título. Se a API falla, os `errors[]` van ao seu campo (por ruta, ou con `controlFor`) e
 * o que non ten campo, a un aviso da sección; o escrito nunca se perde. Cancelar volve aos
 * valores do último estado limpo (a feature reconstrúe os `FormArray` en `cancelled`).
 *
 * Mentres está sucia, a sección conta para `unsavedChangesGuard` e para `beforeunload`.
 *
 * **Modo edición global** (cando hai unha `EditSession` na pantalla): a sección non ten pé propio;
 * os valores vense en texto fóra da edición e como campos dentro, e é a sesión (a barra fixa) quen
 * valida, garda e cancela. Os erros da API seguen indo ao seu campo ou ao aviso da sección.
 */
@Component({
  selector: 'furar-edit-section',
  imports: [ButtonDirective, IconComponent, ProblemAlertComponent, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'f-edit-section', '[attr.data-dirty]': 'dirty() ? "" : null' },
  templateUrl: './edit-section.component.html',
})
export class EditSectionComponent implements DoCheck {
  /** Título da sección (xa traducido); é o nome da rexión. */
  readonly heading = input.required<string>();
  readonly headingLevel = input<2 | 3>(2);
  /** Garda os valores do formulario; resolve ao gardar e rexeita co `ApiProblem` se falla. */
  readonly save = input.required<() => Promise<unknown> | Observable<unknown>>();
  /** Control para un erro da API cando a ruta non casa coa do formulario. */
  readonly controlFor = input<(field: string) => AbstractControl | null>(() => null);

  /** Gardouse ben. */
  readonly saved = output();
  /** Cancelouse: o formulario xa volveu aos valores limpos. */
  readonly cancelled = output();
  /** Fallou o gardado (412, 409…), por se a feature quere ofrecer algo máis ca o aviso. */
  readonly failed = output<ApiProblem>();

  /** O formulario ten cambios sen gardar. */
  readonly dirty = signal(false);
  protected readonly saving = signal(false);
  protected readonly savedNotice = signal(false);
  protected readonly problem = signal<ApiProblem | null>(null);
  protected readonly headingId = `furar-edit-section-${nextId++}`;

  /** Modo edición global da pantalla, se o hai. */
  readonly session = inject(EditSession, { optional: true });
  private readonly directive = inject(FormGroupDirective, { self: true });
  private readonly injector = inject(Injector);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly fields = contentChildren(EditFieldComponent, { descendants: true });
  private readonly headingElement = viewChild<ElementRef<HTMLElement>>('title');
  private bound: FormGroup | null = null;
  private subscription: Subscription | null = null;
  private baseline: unknown = null;
  private noticeTimer: ReturnType<typeof setTimeout> | undefined;

  constructor() {
    const untrack = inject(UnsavedChangesService).track(this.dirty.asReadonly());
    const leave = this.session?.register({
      dirty: this.dirty.asReadonly(),
      validate: () => this.validate(),
      save: () => this.saveInSession(),
      reset: () => this.reset(),
    });
    inject(DestroyRef).onDestroy(() => {
      leave?.();
      untrack();
      this.subscription?.unsubscribe();
      clearTimeout(this.noticeTimer);
    });
  }

  /** A feature pode cambiar o `FormGroup`: séguese sempre o actual. */
  ngDoCheck(): void {
    const form = this.directive.form as FormGroup | null;
    if (form && form !== this.bound) {
      this.subscription?.unsubscribe();
      this.bound = form;
      this.sync();
      this.subscription = form.events.subscribe(() => this.sync());
    }
  }

  protected async onSubmit(event: Event): Promise<void> {
    event.preventDefault();
    if (this.session) {
      // Intro nun campo: garda a pantalla enteira, como «Gardar» na barra.
      if (this.session.editing()) await this.session.save();
      return;
    }
    const form = this.bound;
    if (!form || this.saving() || !form.dirty) {
      return;
    }
    this.problem.set(null);
    form.markAllAsTouched();
    if (form.invalid) {
      this.focusError();
      return;
    }
    this.saving.set(true);
    try {
      const result = this.save()();
      await (isObservable(result) ? firstValueFrom(result, { defaultValue: undefined }) : result);
    } catch (error) {
      this.saving.set(false);
      this.fail(form, asProblem(error));
      return;
    }
    this.saving.set(false);
    form.markAsPristine();
    this.closeFields();
    this.showSaved();
    this.saved.emit();
  }

  /** Modo edición global: marca os erros á vista; falso se hai algún. */
  private validate(): boolean {
    const form = this.bound;
    if (!form || !form.dirty) return true;
    this.problem.set(null);
    form.markAllAsTouched();
    if (form.invalid) this.focusError();
    return form.valid;
  }

  /** Modo edición global: garda; se falla, deixa o erro no sitio e rexeita. */
  private async saveInSession(): Promise<void> {
    const form = this.bound;
    if (!form || !form.dirty) return;
    this.problem.set(null);
    this.saving.set(true);
    try {
      const result = this.save()();
      await (isObservable(result) ? firstValueFrom(result, { defaultValue: undefined }) : result);
    } catch (error) {
      this.saving.set(false);
      this.fail(form, asProblem(error));
      throw error;
    }
    this.saving.set(false);
    form.markAsPristine();
    form.markAsUntouched();
    this.saved.emit();
  }

  /** Modo edición global: volve ao último estado limpo sen mover o foco. */
  private reset(): void {
    const form = this.bound;
    if (!form) return;
    form.reset(this.baseline as never);
    this.problem.set(null);
    this.cancelled.emit();
  }

  protected cancel(): void {
    const form = this.bound;
    if (!form || this.saving()) {
      return;
    }
    form.reset(this.baseline as never);
    this.problem.set(null);
    this.closeFields();
    this.cancelled.emit();
    this.focusHeading();
  }

  private fail(form: FormGroup, problem: ApiProblem): void {
    const unmatched = applyFieldErrors(form, problem, this.controlFor());
    if (form.invalid) {
      form.markAllAsTouched();
      this.focusError();
    }
    if (form.valid || unmatched > 0) {
      this.problem.set(problem);
    }
    this.failed.emit(problem);
  }

  private sync(): void {
    const form = this.bound;
    if (!form) {
      return;
    }
    this.dirty.set(form.dirty);
    if (form.pristine) {
      this.baseline = form.getRawValue();
    } else if (this.savedNotice()) {
      this.savedNotice.set(false);
    }
  }

  private closeFields(): void {
    for (const field of this.fields()) {
      field.close();
    }
  }

  private showSaved(): void {
    clearTimeout(this.noticeTimer);
    this.savedNotice.set(true);
    this.noticeTimer = setTimeout(() => this.savedNotice.set(false), SAVED_NOTICE_MS);
    this.focusHeading();
  }

  /**
   * Foco no título. Despois, o formulario queda sen tocar: o `blur` do campo que se pecha
   * marcaríao tocado e os erros sairían antes de tempo na seguinte edición.
   */
  private focusHeading(): void {
    afterNextRender(
      () => {
        this.headingElement()?.nativeElement.focus();
        this.bound?.markAsUntouched();
      },
      { injector: this.injector },
    );
  }

  private focusError(): void {
    afterNextRender(() => focusFirstError(this.host.nativeElement), { injector: this.injector });
  }
}
