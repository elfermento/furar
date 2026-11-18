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
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { LanguageService } from '../../../../core/i18n/language.service';
import { startWith, switchMap } from 'rxjs';
import { ButtonDirective } from '../../../../shared/ui/button/button.directive';
import { DeleteButtonComponent } from '../../../../shared/ui/delete-button/delete-button.component';
import { focusFirstError } from '../../../../shared/ui/edit-section/section-errors';
import { describedBy, fieldError } from '../../../../shared/ui/field-errors';
import { FieldComponent } from '../../../../shared/ui/field.component';
import { FieldActionComponent } from '../../../../shared/ui/field-group/field-action.component';
import { FieldGroupComponent } from '../../../../shared/ui/field-group/field-group.component';
import { OptionsPipe } from '../../../../shared/ui/select/options.pipe';
import { SelectComponent } from '../../../../shared/ui/select/select.component';
import { HelpTipComponent } from '../../../../shared/ui/help-tip/help-tip.component';
import { IconComponent } from '../../../../shared/ui/icon/icon.component';
import { InputDirective } from '../../../../shared/ui/input/input.directive';
import { ProblemAlertComponent } from '../../../../shared/ui/problem-alert.component';
import { RequiredMarkComponent } from '../../../../shared/ui/required/required.component';
import { RENTAL_MODES, USE_LIMITS, partForm, slotOptions } from '../../uses/use-form';
import { PRICE_KINDS } from '../../uses/use-terms';
import type { UseEditor } from '../wizard-use-editor';
import { WizardUseEditor } from '../wizard-use-editor';
import { UseSportsFieldComponent } from '../../uses/use-sports-field.component';

/**
 * Formulario dun uso dentro do paso «Usos»: nunha tarxeta co bordo azul,
 * «Novo uso» ou «Editar uso» coa súa icona; nome e como se aluga (enteira, por partes ou por
 * prazas) nunha cápsula, os deportes do uso (se a instalación ten varios), e cada reserva e o prezo noutra. Sen «Cando» nin normas: iso queda na ficha. «Engadir
 * uso» (ou «Aceptar») vai á API ao momento; os erros, no seu campo.
 */
@Component({
  selector: 'furar-wizard-use-editor',
  imports: [
    ButtonDirective,
    DeleteButtonComponent,
    FieldActionComponent,
    FieldComponent,
    FieldGroupComponent,
    HelpTipComponent,
    IconComponent,
    InputDirective,
    OptionsPipe,
    ProblemAlertComponent,
    ReactiveFormsModule,
    RequiredMarkComponent,
    SelectComponent,
    TranslocoPipe,
    UseSportsFieldComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './use-editor.component.html',
})
export class UseEditorComponent {
  readonly editor = input.required<UseEditor>();

  protected readonly editing = inject(WizardUseEditor);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly injector = inject(Injector);
  private readonly transloco = inject(TranslocoService);

  protected readonly modes = RENTAL_MODES;
  protected readonly priceKinds = PRICE_KINDS;
  protected readonly limits = USE_LIMITS;
  protected readonly describedBy = describedBy;
  protected readonly lang = inject(LanguageService).lang;
  protected readonly slotOptions = computed(() => {
    this.lang();
    return slotOptions((key, params) => this.transloco.translate(key, params));
  });
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
    this.focusPart(parts.length - 1);
  }

  protected removePart(index: number): void {
    const parts = this.editor().form.controls.parts;
    parts.removeAt(index);
    parts.markAsDirty();
    this.focusPart(Math.max(0, index - 1));
  }

  /** O nome da papeleira dunha parte: «Quitar a parte «Media pista norte»» (sen nome, co número). */
  protected partRemoveLabel(index: number): string {
    const name = (this.editor().form.controls.parts.at(index)?.value.name ?? '').trim();
    return name
      ? this.transloco.translate('facility.uses.parts.remove', { name })
      : this.transloco.translate('facility.uses.parts.removeN', { n: index + 1 });
  }

  /** Despois de engadir ou quitar unha parte: o foco no seu campo (ou no anterior, ou en «Engadir»). */
  private focusPart(index: number): void {
    afterNextRender(
      () => {
        const inputs = this.host.nativeElement.querySelectorAll<HTMLElement>('[data-part] input');
        (
          inputs.item(Math.min(index, inputs.length - 1)) ??
          this.host.nativeElement.querySelector<HTMLElement>('[data-part-add]')
        )?.focus();
      },
      { injector: this.injector },
    );
  }

  protected async apply(): Promise<void> {
    const ok = await this.editing.apply();
    this.applied.update((n) => n + 1);
    if (!ok) afterNextRender(() => focusFirstError(this.host.nativeElement), { injector: this.injector });
  }
}
