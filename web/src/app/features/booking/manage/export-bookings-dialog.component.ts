// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import type { ApiProblem } from '../../../core/errors/api-problem';
import { asProblem } from '../../../core/errors/problem.interceptor';
import { ButtonDirective } from '../../../shared/ui/button/button.directive';
import type { IsoDate } from '../../../shared/ui/calendar/local-date';
import { DialogComponent } from '../../../shared/ui/dialog/dialog.component';
import { MultiComboboxComponent } from '../../../shared/ui/multi-combobox/multi-combobox.component';
import { FilterChipDirective } from '../../../shared/ui/filter-chip/filter-chip.directive';
import { IconComponent } from '../../../shared/ui/icon/icon.component';
import { ProblemAlertComponent } from '../../../shared/ui/problem-alert.component';
import { RequiredMarkComponent } from '../../../shared/ui/required/required.component';
import { ToastService } from '../../../shared/ui/toast/toast.service';
import { DateFilterComponent } from '../../../shared/ui/date-filter/date-filter.component';
import { EXPORT_MAX_MONTHS, exportChoice, exportParams, exportRangeError } from './export-bookings';
import { ExportBookingsStore } from './export-bookings.store';
import type { BookingFilters, FacilityChoice, StateFilter } from './manage-bookings';
import { STATE_OPTIONS, facilityPicker, toggle } from './manage-bookings';

export interface ExportBookingsData {
  readonly slug: string;
  /** Os filtros da lista ao abrir: as datas, as instalacións e os estados saen deles. */
  readonly filters: BookingFilters;
  readonly facilities: readonly FacilityChoice[];
  readonly today: IsoDate;
}

let nextId = 0;

/**
 * «Descargar CSV» das reservas, só a xestión: o período (obrigatorio, como
 * moito doce meses; co mesmo campo de datas ca os filtros, os seus atallos e o calendario propio),
 * as instalacións (`furar-multi-combobox`) e os estados, todo cuberto cos filtros da lista. A API fai o CSV
 * (`ExportBookingsStore`); un aviso di o nome do ficheiro. No móbil, folla desde abaixo.
 */
@Component({
  selector: 'furar-export-bookings-dialog',
  imports: [
    ButtonDirective,
    DateFilterComponent,
    DialogComponent,
    FilterChipDirective,
    MultiComboboxComponent,
    IconComponent,
    ProblemAlertComponent,
    RequiredMarkComponent,
    TranslocoPipe,
  ],
  providers: [ExportBookingsStore],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @let t = 'booking.manage.export.';
    @let f = 'booking.manage.filters.';
    <furar-dialog [heading]="t + 'title' | transloco" [busy]="busy()">
      <div class="f-mfilters">
        <furar-problem-alert [problem]="problem()" />
        <div
          class="f-mfilters__group"
          role="group"
          [attr.aria-labelledby]="ids.dates"
          [attr.aria-describedby]="rangeError() ? ids.error : null"
        >
          <p class="f-mfilters__label" [id]="ids.dates">{{ f + 'dates.label' | transloco }}<furar-required-mark /></p>
          <furar-date-filter [(from)]="from" [(to)]="to" [today]="data.today" />
          @if (rangeError(); as error) {
            <span class="f-field-error" data-export-error [id]="ids.error">
              {{ t + 'error.' + error | transloco: { months: maxMonths } }}
            </span>
          }
        </div>

        @if (data.facilities.length) {
          <furar-multi-combobox
            class="f-mfilters__group"
            data-filter="facility"
            highlight
            debounce="0"
            [fieldId]="ids.facility"
            [label]="f + 'facility' | transloco"
            [chosenLabel]="f + 'facilityChosen' | transloco"
            [source]="facilities.source"
            [key]="facilities.key"
            [display]="facilities.display"
            [countText]="facilities.countText"
            [(selected)]="facilityIds"
          />
        }

        <div class="f-mfilters__group">
          <p class="f-mfilters__label" [id]="ids.state">{{ f + 'state' | transloco }}</p>
          <div class="f-mfilters__chips" role="group" [attr.aria-labelledby]="ids.state" data-filter="state">
            @for (option of stateOptions; track option.value) {
              <button
                type="button"
                [furarFilterChip]="option.tone"
                [attr.data-value]="option.value"
                [pressed]="states().includes(option.value)"
                (pressedChange)="toggleState(option.value)"
              >
                {{ f + 'stateOption.' + option.value | transloco }}
              </button>
            }
          </div>
        </div>
      </div>

      <ng-container dialogActions>
        <button type="button" furarButton="quiet" data-export-cancel (click)="ref.close()">
          {{ t + 'cancel' | transloco }}
        </button>
        <button type="button" furarButton="primary" data-export-download [busy]="busy()" (click)="download()">
          <furar-icon name="download" />
          {{ t + 'download' | transloco }}
        </button>
      </ng-container>
    </furar-dialog>
  `,
})
export class ExportBookingsDialogComponent {
  protected readonly data = inject<ExportBookingsData>(DIALOG_DATA);
  protected readonly ref = inject<DialogRef<boolean>>(DialogRef);
  private readonly store = inject(ExportBookingsStore);
  private readonly toasts = inject(ToastService);

  private readonly id = nextId++;
  private readonly transloco = inject(TranslocoService);
  protected readonly ids = {
    dates: `furar-export-${this.id}-dates`,
    error: `furar-export-${this.id}-error`,
    facility: `furar-export-${this.id}-facility`,
    state: `furar-export-${this.id}-state`,
  };
  protected readonly facilities = facilityPicker(this.data.facilities, (key, params) =>
    this.transloco.translate(key, params),
  );
  protected readonly maxMonths = EXPORT_MAX_MONTHS;
  protected readonly stateOptions = STATE_OPTIONS;
  private readonly facilityOrder = this.data.facilities.map((f) => f.id);
  private readonly stateOrder = STATE_OPTIONS.map((o) => o.value);
  private readonly initial = exportChoice(this.data.filters, this.data.today);

  protected readonly from = signal<IsoDate | null>(this.initial.from);
  protected readonly to = signal<IsoDate | null>(this.initial.to);
  protected readonly facilityIds = signal<readonly string[]>(
    this.initial.facilityIds.filter((id) => this.facilityOrder.includes(id)),
  );
  protected readonly states = signal<readonly StateFilter[]>(this.initial.states);
  protected readonly busy = signal(false);
  protected readonly problem = signal<ApiProblem | null>(null);
  /** «Descargar» xa se intentou: entón tamén se di que faltan as datas. */
  private readonly submitted = signal(false);

  /** O período demasiado longo dise ao momento; o que falta, despois de intentar descargar. */
  protected readonly rangeError = computed(() => {
    const error = exportRangeError(this.from(), this.to());
    return error === 'tooLong' || (error && this.submitted()) ? error : null;
  });

  protected toggleState(value: StateFilter): void {
    this.states.update((list) => toggle(list, value, this.stateOrder));
  }

  protected async download(): Promise<void> {
    if (this.busy()) return;
    this.submitted.set(true);
    const from = this.from();
    const to = this.to();
    if (!from || !to || exportRangeError(from, to)) return;
    this.busy.set(true);
    this.problem.set(null);
    try {
      const file = await this.store.download(
        exportParams(this.data.slug, { from, to, facilityIds: this.facilityIds(), states: this.states() }),
      );
      this.toasts.show('booking.manage.export.done', { file });
      this.ref.close(true);
    } catch (error) {
      this.problem.set(asProblem(error));
      this.busy.set(false);
    }
  }
}
