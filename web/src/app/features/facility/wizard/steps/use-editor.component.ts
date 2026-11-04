// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  Injector,
  afterNextRender,
  computed,
  inject,
  input,
  signal,
} from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { ReactiveFormsModule } from '@angular/forms';
import { TranslocoPipe } from '@jsverse/transloco';
import { startWith, switchMap } from 'rxjs';
import { ButtonDirective } from '../../../../shared/ui/button/button.directive';
import { DeleteButtonComponent } from '../../../../shared/ui/delete-button/delete-button.component';
import { focusFirstError } from '../../../../shared/ui/edit-section/section-errors';
import { describedBy, fieldError } from '../../../../shared/ui/field-errors';
import { FieldComponent } from '../../../../shared/ui/field.component';
import { IconComponent } from '../../../../shared/ui/icon/icon.component';
import { InputDirective } from '../../../../shared/ui/input/input.directive';
import { ProblemAlertComponent } from '../../../../shared/ui/problem-alert.component';
import { RequiredMarkComponent } from '../../../../shared/ui/required/required.component';
import {
  SegmentedOptionDirective,
  SegmentedRadioDirective,
} from '../../../../shared/ui/segmented/segmented-radio.directive';
import { SegmentedDirective } from '../../../../shared/ui/segmented/segmented.directive';
import { RENTAL_MODES, SLOT_MINUTES, USE_LIMITS, partForm } from '../../uses/use-form';
import { PRICE_KINDS } from '../../uses/use-terms';
import type { UseEditor } from '../wizard-use-editor';
import { WizardUseEditor } from '../wizard-use-editor';

/**
 * Formulario dun uso dentro do paso «Usos» (maqueta 53): nome, como se aluga (enteira, por partes
 * ou por prazas), cada reserva e o prezo. Sen «Cando» nin normas: iso queda na ficha. «Engadir
 * uso» (ou «Aceptar») vai á API ao momento; os erros, no seu campo.
 */
@Component({
  selector: 'furar-wizard-use-editor',
  imports: [
    ButtonDirective,
    DeleteButtonComponent,
    FieldComponent,
    IconComponent,
    InputDirective,
    ProblemAlertComponent,
    ReactiveFormsModule,
    RequiredMarkComponent,
    SegmentedDirective,
    SegmentedOptionDirective,
    SegmentedRadioDirective,
    TranslocoPipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './use-editor.component.html',
})
export class UseEditorComponent {
  readonly editor = input.required<UseEditor>();

  protected readonly editing = inject(WizardUseEditor);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly injector = inject(Injector);

  protected readonly modes = RENTAL_MODES;
  protected readonly slots = SLOT_MINUTES;
  protected readonly priceKinds = PRICE_KINDS;
  protected readonly limits = USE_LIMITS;
  protected readonly describedBy = describedBy;
  private readonly applied = signal(0);
  private readonly events = toSignal(
    toObservable(this.editor).pipe(switchMap((editor) => editor.form.events.pipe(startWith(null)))),
  );

  protected readonly value = computed(() => {
    this.events();
    return this.editor().form.getRawValue();
  });
  protected readonly errors = computed(() => {
    this.events();
    this.applied();
    const c = this.editor().form.controls;
    return {
      name: fieldError(c.name, false),
      capacity: fieldError(c.capacity, false),
      parts: fieldError(c.parts, false),
      partNames: c.parts.controls.map((part) => fieldError(part.controls.name, false)),
      amount: fieldError(c.price.controls.amountCents, false),
    };
  });

  constructor() {
    afterNextRender(() => this.host.nativeElement.querySelector<HTMLElement>('#wizard-use-name')?.focus(), {
      injector: this.injector,
    });
  }

  protected addPart(): void {
    const parts = this.editor().form.controls.parts;
    parts.push(partForm());
    parts.markAsDirty();
    this.focusLast();
  }

  protected removePart(index: number): void {
    const parts = this.editor().form.controls.parts;
    parts.removeAt(index);
    parts.markAsDirty();
    this.focusLast();
  }

  protected async apply(): Promise<void> {
    const ok = await this.editing.apply();
    this.applied.update((n) => n + 1);
    if (!ok) afterNextRender(() => focusFirstError(this.host.nativeElement), { injector: this.injector });
  }

  private focusLast(): void {
    afterNextRender(
      () => [...this.host.nativeElement.querySelectorAll<HTMLElement>('[data-part] input')].pop()?.focus(),
      { injector: this.injector },
    );
  }
}
