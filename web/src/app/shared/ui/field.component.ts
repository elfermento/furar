// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, ElementRef, booleanAttribute, inject, input } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import type { ControlError } from '../custom-fields/custom-field-controls';
import { HelpTipComponent } from './help-tip/help-tip.component';
import { RequiredMarkComponent } from './required/required.component';

/**
 * Envoltorio accesible para un control proxectado: etiqueta visible ligada co `for`,
 * axuda e erro con id estables (o control pon aria-describedby con describedBy()).
 * Rótulo sempre enriba, nunca flotante, e nunha liña; obrigatorio cun «*» (docs/deseno.md P1-3);
 * axuda entre o rótulo e o control, erro debaixo. Grella de catro filas (rótulo, axuda, control, erro): dentro
 * dunha `.f-field-row` usa as filas da fila (subgrid), así os controis quedan á mesma altura
 * aínda que un rótulo ou unha axuda ocupen máis. O control leva [furarInput]. O que leve o atributo
 * `fieldLabelAside` (p. ex. un «?» de axuda, `furar-field-popover look="help"`) vai na liña do
 * rótulo, fóra do `<label>` (un botón dentro del activaría o campo). Con `tip`, a axuda non vai
 * debaixo do rótulo senón nun «?» ao seu carón (`furar-help-tip`), co mesmo id `<fieldId>-help`:
 * o control segue describíndose con `describedBy(id, true, error)`.
 *
 * Sistema de campos (styles/components/fields.css): se o control é un campo (input, textarea,
 * `furar-select`, `furar-date-field`, `furar-time-picker`, combobox) vai nunha cápsula co rótulo
 * dentro; picar en calquera sitio da cápsula leva ao control (ou abre o despregable). A axuda
 * visible e o erro van debaixo, fóra da cápsula; dentro dun `furar-field-group`, o erro vai xusto
 * debaixo do seu campo, dentro do grupo (o seguinte baixa). Con `affix="tight"`, o valor
 * cunha unidade (`.f-field-affix`) vai co ancho das súas cifras e a unidade pegada.
 */
@Component({
  selector: 'furar-field',
  host: { class: 'f-field', '[attr.data-affix]': 'affix()', '(click)': 'onClick($event)' },
  imports: [HelpTipComponent, RequiredMarkComponent, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="f-field__head">
      <label class="f-label" [for]="fieldId()"
        >{{ label() }}
        @if (required()) {
          <furar-required-mark />
        }
      </label>
      @if (tip()) {
        <furar-help-tip
          [label]="tipLabel() || ('ui.helpTip.label' | transloco: { label: label() })"
          [text]="tip()"
          [textId]="fieldId() + '-help'"
        />
      }
      <ng-content select="[fieldLabelAside]" />
    </div>
    @if (help() && !tip()) {
      <span class="f-help" [id]="fieldId() + '-help'">{{ help() }}</span>
    }
    <div class="f-field__control"><ng-content /></div>
    @if (error(); as e) {
      <span class="f-field-error" [id]="fieldId() + '-error'">{{ e.key | transloco: e.params }}</span>
    }
  `,
})
export class FieldComponent {
  readonly fieldId = input.required<string>();
  readonly label = input.required<string>();
  readonly help = input<string>('');
  /** Axuda nun «?» ao carón do rótulo, en vez de debaixo (gaña a `help`). */
  readonly tip = input<string>('');
  /** Nome do «?» (unha pregunta: «Que é o DPD?»); por defecto, «Axuda sobre «rótulo»». */
  readonly tipLabel = input<string>('');
  readonly required = input(false, { transform: booleanAttribute });
  readonly error = input<ControlError | null>(null);
  /**
   * Cun valor e a súa unidade (`.f-field-affix`), `tight`: o número co ancho das súas cifras e a
   * unidade pegada detrás, para lelo coma unha frase («Polo menos | 2 horas antes»).
   */
  readonly affix = input<'tight' | null>(null);

  private readonly element = inject<ElementRef<HTMLElement>>(ElementRef).nativeElement;

  /** Picar na cápsula fóra do control (o rótulo xa vai co `for`): foco no control ou abrir o despregable. */
  protected onClick(event: MouseEvent): void {
    const target = event.target as Element | null;
    if (
      !target ||
      target.closest(
        'a, button, input, select, textarea, label, [contenteditable], .f-help, .f-field-error, ' +
          // Dentro dunha capa do campo (o calendario, a lista): o clic é dela, non da cápsula.
          '[role="dialog"], [role="listbox"], [role="grid"], [role="menu"]',
      )
    ) {
      return;
    }
    const control = this.element.ownerDocument.getElementById(this.fieldId());
    if (!control || !this.element.contains(control) || control.matches(':disabled')) {
      return;
    }
    if (control instanceof HTMLButtonElement) {
      control.click();
    } else {
      control.focus();
    }
  }
}
