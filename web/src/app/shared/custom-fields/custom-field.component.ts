// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import type { FormControl } from '@angular/forms';
import { ReactiveFormsModule } from '@angular/forms';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { map, merge, of, switchMap } from 'rxjs';
import type { SelectOption } from '../ui/select/select.component';
import { HelpTipComponent } from '../ui/help-tip/help-tip.component';
import { DateFieldComponent } from '../ui/date-field/date-field.component';
import { RequiredMarkComponent } from '../ui/required/required.component';
import { SelectComponent } from '../ui/select/select.component';
import { yesNoOptions } from '../ui/select/select-option';
import { SwitchComponent } from '../ui/switch/switch.component';
import { firstError, present } from './custom-field-controls';
import type { CustomFieldDefinition, CustomFieldValue } from './custom-field.types';
import { localized } from './localized';

/**
 * Un campo personalizado accesible: etiqueta visible, axuda e erro ligados con
 * aria-describedby, obrigatorio indicado en texto, grupos de opcións con fieldset/legend.
 */
@Component({
  selector: 'furar-custom-field',
  imports: [
    ReactiveFormsModule,
    RequiredMarkComponent,
    TranslocoPipe,
    SelectComponent,
    SwitchComponent,
    DateFieldComponent,
    HelpTipComponent,
  ],
  host: { class: 'f-field', '[attr.data-label-empty]': "labelEmpty() ? '' : null" },
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './custom-field.component.html',
})
export class CustomFieldComponent {
  readonly definition = input.required<CustomFieldDefinition>();
  readonly control = input.required<FormControl<CustomFieldValue>>();
  readonly lang = input.required<string>();
  readonly idPrefix = input('cf');
  /** Amosar erros aínda que o campo non se tocase (despois de enviar). */
  readonly showErrors = input(false);

  protected readonly id = computed(() => `${this.idPrefix()}-${this.definition().code}`);
  protected readonly helpId = computed(() => `${this.id()}-help`);
  protected readonly errorId = computed(() => `${this.id()}-error`);
  /**
   * Rótulo provisional mentres a etiqueta está baleira (a vista previa de «Novo campo»: «Etiqueta»),
   * no ton apagado dun placeholder: o control nunca queda sen nome.
   */
  readonly emptyLabel = input('');
  private readonly ownLabel = computed(() => localized(this.definition().label, this.lang()).trim());
  protected readonly labelEmpty = computed(() => !this.ownLabel() && !!this.emptyLabel());
  protected readonly label = computed(() => this.ownLabel() || this.emptyLabel());
  protected readonly help = computed(() => localized(this.definition().help, this.lang()));

  /** Cambia co estado do control (valor, tocado, erros da API). */
  private readonly status = toSignal(
    toObservable(this.control).pipe(
      switchMap((control) =>
        merge(of(null), control.events).pipe(map(() => ({ errors: control.errors, touched: control.touched }))),
      ),
    ),
    { initialValue: { errors: null, touched: false } },
  );

  protected readonly error = computed(() => {
    const { touched } = this.status();
    if (!touched && !this.showErrors()) {
      return null;
    }
    return firstError(this.control());
  });

  protected readonly describedBy = computed(() => {
    const ids = [this.help() ? this.helpId() : '', this.error() ? this.errorId() : ''].filter(Boolean);
    return ids.length ? ids.join(' ') : null;
  });

  /** Opcións para escoller: as desactivadas só aparecen se xa estaban escollidas. */
  protected readonly options = computed(() => {
    const value = this.control().value;
    const chosen = Array.isArray(value) ? value : [value];
    return this.definition()
      .options.filter((option) => !option.inactive || chosen.includes(option.code))
      .map((option) => ({ code: option.code, label: localized(option.label, this.lang()) }));
  });

  /** «Escoller» (null, para deixalo baleiro) e as opcións, para o despregable. */
  private readonly transloco = inject(TranslocoService);
  private readonly choose = toSignal(this.transloco.selectTranslate<string>('customFields.form.choose'), {
    initialValue: '',
  });
  /** As opcións do despregable múltiple (sen «Escolle unha opción»). */
  protected readonly multiOptions = computed((): SelectOption<string>[] =>
    this.options().map((option) => ({ value: option.code, label: option.label })),
  );
  /** Si/non opcional: «Sen indicar», «Si» e «Non» (os mesmos ca «Cuberta»). */
  private readonly translations = toSignal(this.transloco.langChanges$);
  protected readonly yesNoOptions = computed(() => {
    this.translations();
    return yesNoOptions((key) => this.transloco.translate(key));
  });
  protected readonly selectOptions = computed((): SelectOption<string | null>[] => [
    { value: null, label: this.choose() },
    ...this.options().map((option) => ({ value: option.code, label: option.label })),
  ]);

  protected readonly step = computed(() => {
    const decimals = this.definition().rules.decimals;
    if (!present(decimals)) {
      return 'any';
    }
    return decimals === 0 ? '1' : (1 / 10 ** decimals).toFixed(decimals);
  });
}
