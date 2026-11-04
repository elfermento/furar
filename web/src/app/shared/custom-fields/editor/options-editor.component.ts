// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import {
  afterNextRender,
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  inject,
  Injector,
  input,
} from '@angular/core';
import type { FormArray } from '@angular/forms';
import { ReactiveFormsModule } from '@angular/forms';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { ActiveToggleButtonComponent } from '../../ui/active-toggle-button/active-toggle-button.component';
import { ButtonDirective } from '../../ui/button/button.directive';
import { FieldsetDirective } from '../../ui/card/fieldset.directive';
import { DeleteButtonComponent } from '../../ui/delete-button/delete-button.component';
import { DialogService } from '../../ui/dialog/dialog.service';
import { describedBy, fieldError } from '../../ui/field-errors';
import { IconComponent } from '../../ui/icon/icon.component';
import { RequiredMarkComponent } from '../../ui/required/required.component';
import type { OptionForm } from './editor-form';
import { optionForm } from './editor-form';

/**
 * Opcións dunha selección como táboa compacta (maqueta 57): unha cabeceira (Código · Galego ·
 * Castelán) e unha fila por opción cos seus campos e, ao final, os botóns só con icona. Unha
 * gardada desactívase e actívase; eliminala pregunta sempre e, se ten valores, só se pode
 * desactivar (a API compróbao ao gardar: `customfield.option-has-values`). Unha nova quítase sen
 * máis.
 */
@Component({
  selector: 'furar-options-editor',
  imports: [
    ActiveToggleButtonComponent,
    ButtonDirective,
    DeleteButtonComponent,
    FieldsetDirective,
    IconComponent,
    ReactiveFormsModule,
    RequiredMarkComponent,
    TranslocoPipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './options-editor.component.html',
})
export class OptionsEditorComponent {
  private readonly injector = inject(Injector);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly dialogs = inject(DialogService);
  private readonly transloco = inject(TranslocoService);
  readonly options = input.required<FormArray<OptionForm>>();
  readonly submitted = input(false);
  /** Idioma da interface: o nome da opción nos botóns e no diálogo. */
  readonly lang = input('gl');
  protected readonly fieldError = fieldError;
  protected readonly describedBy = describedBy;
  protected readonly fields = ['code', 'labelGl', 'labelEs'] as const;

  /** Nome da opción para os botóns: a etiqueta no idioma da interface ou, sen ela, o número. */
  protected name(option: OptionForm, index: number): string {
    const { labelGl, labelEs, code } = option.getRawValue();
    const label = (this.lang() === 'es' ? labelEs || labelGl : labelGl || labelEs).trim();
    return label || code || String(index + 1);
  }

  protected add(): void {
    this.options().push(optionForm());
    const index = this.options().length - 1;
    afterNextRender(() => this.host.nativeElement.querySelector<HTMLInputElement>(`#option-${index}-code`)?.focus(), {
      injector: this.injector,
    });
  }

  protected toggle(option: OptionForm): void {
    option.controls.inactive.setValue(!option.controls.inactive.value);
    option.markAsDirty();
  }

  /** Unha nova quítase; unha gardada pregunta e, con valores, ofrece desactivala. */
  protected async remove(index: number): Promise<void> {
    const option = this.options().at(index);
    if (!option.controls.saved.value) {
      this.drop(index);
      return;
    }
    const name = this.name(option, index);
    const t = (key: string) => this.transloco.translate(`customFields.editor.option.${key}`, { name });
    if (option.controls.hasValues.value) {
      const inactive = option.controls.inactive.value;
      const deactivate = await this.dialogs.confirm({
        heading: t('cannotDeleteTitle'),
        message: t(inactive ? 'cannotDeleteInactive' : 'cannotDelete'),
        confirmLabel: this.transloco.translate(inactive ? 'ui.dialog.close' : 'customFields.editor.option.deactivate'),
        informOnly: inactive,
      });
      if (deactivate && !inactive) {
        this.toggle(option);
      }
      return;
    }
    const confirmed = await this.dialogs.confirm({
      heading: t('deleteTitle'),
      message: t('deleteMessage'),
      confirmLabel: this.transloco.translate('customFields.editor.option.delete'),
      tone: 'danger',
    });
    if (confirmed) {
      this.drop(index);
    }
  }

  private drop(index: number): void {
    this.options().removeAt(index);
    this.options().markAsDirty();
    this.host.nativeElement.querySelector<HTMLButtonElement>('#option-add')?.focus();
  }
}
