// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import type { ControlError } from '../../../shared/custom-fields/custom-field-controls';
import { MAX_AGE } from '../../../shared/dependents/dependents.store';
import { ParticipantListComponent } from '../../../shared/dependents/participant-list.component';
import type { CompanionDraft, ParticipantOption } from '../../../shared/dependents/participants';
import { MAX_SEATS, ParticipantsSelection, SELF, validBirthYear } from '../../../shared/dependents/participants';
import { ButtonDirective } from '../../../shared/ui/button/button.directive';
import { DeleteButtonComponent } from '../../../shared/ui/delete-button/delete-button.component';
import { DialogService } from '../../../shared/ui/dialog/dialog.service';
import { FieldGroupComponent } from '../../../shared/ui/field-group/field-group.component';
import { FieldComponent } from '../../../shared/ui/field.component';
import { IconComponent } from '../../../shared/ui/icon/icon.component';
import { InputDirective } from '../../../shared/ui/input/input.directive';
import type { HolderDependentResponse } from './book-for.store';

/** Se o acompañante está ben escrito: nome e un ano de nacemento válido. */
export function companionValid(companion: CompanionDraft): boolean {
  return !!companion.name.trim() && validBirthYear(companion.year, new Date().getFullYear(), MAX_AGE);
}

/**
 * «Prazas» do formulario de reservar no nome doutra persoa, nos usos por prazas: a
 * persoa a nome de quen vai a reserva, as persoas a cargo da conta do seu correo (se as ten) e os
 * acompañantes que o persoal escribe só para esta reserva, fila a fila, co nome e o ano. Cada
 * persoa ocupa unha praza. As persoas a cargo e os acompañantes nunca levan correo (ADR-0031).
 */
@Component({
  selector: 'furar-book-for-seats',
  imports: [
    ButtonDirective,
    DeleteButtonComponent,
    FieldComponent,
    FieldGroupComponent,
    IconComponent,
    InputDirective,
    ParticipantListComponent,
    TranslocoPipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @let t = 'booking.who.companion.';
    @let id = ids();
    <div class="f-seats" data-book-for-seats>
      <span class="f-label">{{ 'booking.bookFor.seats' | transloco }}</span>
      <furar-participant-list
        [label]="'booking.bookFor.seats' | transloco"
        [options]="options()"
        [selected]="who.selected()"
        (toggled)="who.toggle($event)"
      />
      @for (companion of who.companions(); track $index; let i = $index) {
        <div class="f-seats__companion" data-book-for-companion>
          <furar-field-group cells [style.--f-cell]="'8rem'">
            <furar-field
              [fieldId]="id + '-cn' + i"
              [label]="'dependents.name' | transloco"
              [tip]="t + 'hint' | transloco"
              [error]="nameError(companion)"
            >
              <input
                furarInput
                autocomplete="off"
                maxlength="200"
                data-companion-name
                [id]="id + '-cn' + i"
                [value]="companion.name"
                [attr.aria-invalid]="nameError(companion) ? 'true' : null"
                [attr.aria-describedby]="
                  id + '-cn' + i + '-help' + (nameError(companion) ? ' ' + id + '-cn' + i + '-error' : '')
                "
                (input)="edit(i, 'name', $event)"
              />
            </furar-field>
            <furar-field [fieldId]="id + '-cy' + i" [label]="t + 'year' | transloco" [error]="yearError(companion)">
              <input
                furarInput
                autocomplete="off"
                inputmode="numeric"
                maxlength="4"
                data-companion-year
                [id]="id + '-cy' + i"
                [value]="companion.year"
                [attr.aria-invalid]="yearError(companion) ? 'true' : null"
                [attr.aria-describedby]="yearError(companion) ? id + '-cy' + i + '-error' : null"
                (input)="edit(i, 'year', $event)"
              />
            </furar-field>
          </furar-field-group>
          <!-- Quitar, debaixo da fila e á dereita (un botón non vai na liña dos campos). -->
          <furar-delete-button
            class="f-seats__remove"
            data-companion-remove
            [label]="removeLabel(companion)"
            [tooltip]="t + 'remove' | transloco"
            (pressed)="remove(i)"
          />
        </div>
      }
      <div class="mt-unit flex justify-center">
        <button type="button" furarButton size="sm" data-book-for-companion-add [disabled]="full()" (click)="add()">
          <furar-icon name="plus" />
          {{ t + 'add' | transloco }}
        </button>
      </div>
    </div>
  `,
})
export class BookForSeatsComponent {
  /** Prefixo dos ids dos campos. */
  readonly ids = input.required<string>();
  /** O nome da persoa a nome de quen vai a reserva (o que se está a escribir). */
  readonly holderName = input('');
  /** As persoas a cargo da conta do correo; baleira sen conta ou sen fichas. */
  readonly people = input<readonly HolderDependentResponse[]>([]);
  /** Reserva nova: a persoa ocupa praza sempre. Ao cambiar, pódese desmarcar. */
  readonly holderFixed = input(true);
  /** Xa se intentou gardar: os erros dos acompañantes vense. */
  readonly submitted = input(false);

  protected readonly who = inject(ParticipantsSelection);
  private readonly dialogs = inject(DialogService);
  private readonly transloco = inject(TranslocoService);
  private readonly langChanges = toSignal(this.transloco.langChanges$);

  protected readonly options = computed<ParticipantOption[]>(() => {
    this.langChanges();
    return [
      {
        id: SELF,
        label: this.holderName().trim() || this.transloco.translate('booking.who.holder'),
        fixed: this.holderFixed(),
      },
      ...this.people().map((person) => ({ id: person.id, label: person.name, detail: String(person.birthYear) })),
    ];
  });
  protected readonly full = computed(() => this.who.seats() >= MAX_SEATS);

  protected nameError(companion: CompanionDraft): ControlError | null {
    return this.submitted() && !companion.name.trim() ? { key: 'errors.validation.required', params: {} } : null;
  }

  protected yearError(companion: CompanionDraft): ControlError | null {
    return this.submitted() && !validBirthYear(companion.year, new Date().getFullYear(), MAX_AGE)
      ? { key: 'errors.people.dependent-birth-year-invalid', params: {} }
      : null;
  }

  protected removeLabel(companion: CompanionDraft): string {
    this.langChanges();
    const name = companion.name.trim();
    return name
      ? this.transloco.translate('booking.who.companion.removeName', { name })
      : this.transloco.translate('booking.who.companion.remove');
  }

  protected add(): void {
    this.who.addCompanion();
  }

  protected edit(index: number, field: 'name' | 'year', event: Event): void {
    this.who.editCompanion(index, { [field]: (event.target as HTMLInputElement).value });
  }

  /** Quitar: se a fila xa ten algo escrito, pregunta antes. */
  protected async remove(index: number): Promise<void> {
    const companion = this.who.companions()[index];
    const name = companion?.name.trim();
    if (name || companion?.year.trim()) {
      const sure = await this.dialogs.confirm({
        heading: this.transloco.translate('booking.who.companion.removeTitle', {
          name: name || this.transloco.translate('booking.who.companion.add'),
        }),
        confirmLabel: this.transloco.translate('booking.who.companion.remove'),
        tone: 'danger',
      });
      if (!sure) return;
    }
    this.who.removeCompanion(index);
  }
}
