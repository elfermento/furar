// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import type { ParticipantOption } from './participants';

/**
 * A lista de «Para quen»: as persoas que se poden marcar, varias á vez, cada
 * unha coa súa marca e, á dereita, o seu detalle (o ano de nacemento). A mesma peza ao reservar e
 * ao reservar no nome doutra persoa. Lista de selección múltiple (APG «listbox»): cada opción
 * márcase co clic, Intro ou Espazo; unha opción `fixed` está sempre marcada e non se toca.
 */
@Component({
  selector: 'furar-participant-list',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'contents' },
  template: `
    <ul class="f-participants" role="listbox" aria-multiselectable="true" [attr.aria-label]="label()">
      @for (option of options(); track option.id) {
        <li
          class="f-select__option"
          role="option"
          [attr.tabindex]="option.fixed ? null : 0"
          [attr.aria-selected]="option.fixed || selected().includes(option.id)"
          [attr.aria-disabled]="disabled() && !option.fixed ? 'true' : null"
          [attr.data-fixed]="option.fixed ? '' : null"
          [attr.data-participant]="option.id"
          (click)="toggle(option)"
          (keydown.enter)="toggle(option, $event)"
          (keydown.space)="toggle(option, $event)"
        >
          {{ option.label }}
          @if (option.detail) {
            <small>{{ option.detail }}</small>
          }
        </li>
      }
    </ul>
  `,
})
export class ParticipantListComponent {
  /** Nome accesible da lista («Para quen», «Prazas»). */
  readonly label = input.required<string>();
  readonly options = input.required<readonly ParticipantOption[]>();
  /** Os ids marcados. */
  readonly selected = input.required<readonly string[]>();
  /** Mentres se garda: non se marca nin se desmarca. */
  readonly disabled = input(false);
  readonly toggled = output<string>();

  protected toggle(option: ParticipantOption, event?: Event): void {
    event?.preventDefault();
    if (!option.fixed && !this.disabled()) this.toggled.emit(option.id);
  }
}
