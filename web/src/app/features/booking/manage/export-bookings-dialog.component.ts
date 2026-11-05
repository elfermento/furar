// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import type { ApiProblem } from '../../../core/errors/api-problem';
import { asProblem } from '../../../core/errors/problem.interceptor';
import { ButtonDirective } from '../../../shared/ui/button/button.directive';
import type { IsoDate } from '../../../shared/ui/calendar/local-date';
import { DialogComponent } from '../../../shared/ui/dialog/dialog.component';
import { FilterChipDirective } from '../../../shared/ui/filter-chip/filter-chip.directive';
import { IconComponent } from '../../../shared/ui/icon/icon.component';
import { ProblemAlertComponent } from '../../../shared/ui/problem-alert.component';
import { RequiredMarkComponent } from '../../../shared/ui/required/required.component';
import { ToastService } from '../../../shared/ui/toast/toast.service';
import { DateFilterComponent } from './date-filter.component';
import { EXPORT_MAX_MONTHS, exportChoice, exportParams, exportRangeError } from './export-bookings';
import { ExportBookingsStore } from './export-bookings.store';
import type { BookingFilters, StateFilter } from './manage-bookings';
import { STATE_OPTIONS, toggle } from './manage-bookings';

export interface ExportBookingsData {
  readonly slug: string;
  /** Os filtros da lista ao abrir: as datas, as instalacións e os estados saen deles. */
  readonly filters: BookingFilters;
  readonly facilities: readonly { readonly id: string; readonly name: string }[];
  readonly today: IsoDate;
}

let nextId = 0;

/**
 * «Descargar CSV» das reservas (maqueta 60, opción A), só a xestión: o período (obrigatorio, como
 * moito doce meses; co mesmo campo de datas ca os filtros, os seus atallos e o calendario propio),
 * as instalacións e os estados, todo cuberto cos filtros da lista. A API fai o CSV
 * (`ExportBookingsStore`); un aviso di o nome do ficheiro. No móbil, folla desde abaixo.
 */
@Component({
  selector: 'furar-export-bookings-dialog',
  imports: [
    ButtonDirective,
    DateFilterComponent,
    DialogComponent,
    FilterChipDirective,
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
          <furar-booking-date-filter [(from)]="from" [(to)]="to" [today]="data.today" />
          @if (rangeError(); as error) {
            <span class="f-field-error" data-export-error [id]="ids.error">
              {{ t + 'error.' + error | transloco: { months: maxMonths } }}
            </span>
          }
        </div>

        @if (data.facilities.length) {
          <div class="f-mfilters__group">
            <p class="f-mfilters__label" [id]="ids.facility">{{ f + 'facility' | transloco }}</p>
            <div class="f-mfilters__chips" role="group" [attr.aria-labelledby]="ids.facility" data-filter="facility">
              @for (facility of data.facilities; track facility.id) {
                <button
                  type="button"
                  furarFilterChip
                  [attr.data-value]="facility.id"
                  [pressed]="facilityIds().includes(facility.id)"
                  (pressedChange)="toggleFacility(facility.id)"
                >
                  {{ facility.name }}
                </button>
              }
              <button type="button" class="f-mfilters__all" data-all="facility" (click)="allFacilities()">
                {{ f + (allFacilitiesOn() ? 'noneFemale' : 'allFemale') | transloco }}
              </button>
            </div>
          </div>
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
  protected readonly ids = {
    dates: `furar-export-${this.id}-dates`,
    error: `furar-export-${this.id}-error`,
    facility: `furar-export-${this.id}-facility`,
    state: `furar-export-${this.id}-state`,
  };
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

  protected readonly allFacilitiesOn = computed(() => this.facilityIds().length === this.facilityOrder.length);
  /** O período demasiado longo dise ao momento; o que falta, despois de intentar descargar. */
  protected readonly rangeError = computed(() => {
    const error = exportRangeError(this.from(), this.to());
    return error === 'tooLong' || (error && this.submitted()) ? error : null;
  });

  protected toggleFacility(id: string): void {
    this.facilityIds.update((list) => toggle(list, id, this.facilityOrder));
  }

  protected toggleState(value: StateFilter): void {
    this.states.update((list) => toggle(list, value, this.stateOrder));
  }

  protected allFacilities(): void {
    this.facilityIds.set(this.allFacilitiesOn() ? [] : this.facilityOrder);
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
