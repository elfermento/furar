// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, inject, input } from '@angular/core';
import { ReactiveFormsModule } from '@angular/forms';
import { TranslocoPipe } from '@jsverse/transloco';
import { LanguageService } from '../../../core/i18n/language.service';
import { CustomFieldsFormComponent } from '../../../shared/custom-fields/custom-fields-form.component';
import { describedBy, fieldError } from '../../../shared/ui/field-errors';
import { FieldComponent } from '../../../shared/ui/field.component';
import { InputDirective } from '../../../shared/ui/input/input.directive';
import { ReserveStore } from '../reserve/reserve.store';
import type { HolderForm } from './book-for.store';
import type { ManagedBookingDetailResponse } from './manage-bookings';

/**
 * «Para quen?» do formulario de reservar no nome doutra persoa (maqueta 58): nome (obrigatorio),
 * correo (para a confirmación) e teléfono, e despois os campos propios da reserva (os do
 * `ReserveStore` da páxina). Nunha reserva feita coa conta da persoa, o nome e o correo son os da
 * conta: só se ven. Sen consentimentos (ADR-0017): os datos son para xestionar a reserva.
 */
@Component({
  selector: 'furar-book-for-who',
  imports: [CustomFieldsFormComponent, FieldComponent, InputDirective, ReactiveFormsModule, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @let t = 'booking.bookFor.';
    @let id = ids();
    <fieldset class="f-book-for__who" data-book-for-who>
      <legend>{{ t + 'who' | transloco }}</legend>
      @if (account(); as o) {
        <p class="f-book-for__account" data-book-for-account>
          <b>{{ o.holderName }}</b>
          @if (o.holderEmail) {
            <span>{{ o.holderEmail }}</span>
          }
        </p>
      } @else {
        <div class="f-book-for__stack" [formGroup]="form()">
          <furar-field
            [fieldId]="id + '-name'"
            [label]="t + 'name' | transloco"
            [required]="true"
            [error]="error('name')"
          >
            <input
              furarInput
              formControlName="name"
              autocomplete="off"
              aria-required="true"
              data-book-for-name
              [id]="id + '-name'"
              [attr.aria-invalid]="error('name') ? 'true' : null"
              [attr.aria-describedby]="described('name')"
            />
          </furar-field>
          <furar-field
            [fieldId]="id + '-email'"
            [label]="t + 'email' | transloco"
            [help]="t + 'emailHelp' | transloco"
            [error]="error('email')"
          >
            <input
              furarInput
              type="email"
              formControlName="email"
              autocomplete="off"
              data-book-for-email
              [id]="id + '-email'"
              [attr.aria-invalid]="error('email') ? 'true' : null"
              [attr.aria-describedby]="described('email', true)"
            />
          </furar-field>
          <furar-field [fieldId]="id + '-phone'" [label]="t + 'phone' | transloco" [error]="error('phone')">
            <input
              furarInput
              type="tel"
              formControlName="phone"
              autocomplete="off"
              data-book-for-phone
              [id]="id + '-phone'"
              [attr.aria-invalid]="error('phone') ? 'true' : null"
              [attr.aria-describedby]="described('phone')"
            />
          </furar-field>
        </div>
      }
      @if (reserve.fields.active().length) {
        <div data-book-for-custom-fields>
          <furar-custom-fields-form
            [definitions]="reserve.fields.active()"
            [form]="reserve.fields.form()"
            [lang]="lang()"
            [idPrefix]="id + '-cf'"
            [showErrors]="reserve.fields.showErrors()"
          />
        </div>
      }
    </fieldset>
  `,
})
export class BookForWhoComponent {
  readonly form = input.required<HolderForm>();
  /** Prefixo dos ids dos campos. */
  readonly ids = input.required<string>();
  /** Xa se intentou gardar: os erros vense aínda que o campo non se tocase. */
  readonly submitted = input(false);
  /** A reserva feita coa conta da persoa (os seus datos só se ven); `null` se non. */
  readonly account = input<ManagedBookingDetailResponse | null>(null);

  protected readonly reserve = inject(ReserveStore);
  protected readonly lang = inject(LanguageService).lang;

  protected error(name: 'name' | 'email' | 'phone') {
    return fieldError(this.form().controls[name], this.submitted());
  }

  protected described(name: 'name' | 'email' | 'phone', hasHelp = false): string | null {
    return describedBy(`${this.ids()}-${name}`, hasHelp, this.error(name));
  }
}
