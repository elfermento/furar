// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { TranslocoPipe } from '@jsverse/transloco';
import type { ApiProblem } from '../../../core/errors/api-problem';
import { asProblem } from '../../../core/errors/problem.interceptor';
import { ButtonDirective } from '../../../shared/ui/button/button.directive';
import type { IsoDate } from '../../../shared/ui/calendar/local-date';
import { inMadrid, madridToday } from '../../../shared/ui/calendar/madrid-time';
import { DateFieldComponent } from '../../../shared/ui/date-field/date-field.component';
import { DialogComponent } from '../../../shared/ui/dialog/dialog.component';
import { fieldError } from '../../../shared/ui/field-errors';
import { FieldComponent } from '../../../shared/ui/field.component';
import { IconComponent } from '../../../shared/ui/icon/icon.component';
import { ProblemAlertComponent } from '../../../shared/ui/problem-alert.component';
import type { SeriesResponse } from './seasons.store';
import { SeasonsStore } from './seasons.store';

/** A tempada que se remata, o concello e, se se saben, as sesións que lle quedan. */
export interface EndSeriesData {
  readonly series: SeriesResponse;
  readonly slug: string;
  /** O inicio de cada sesión confirmada por vir; sen elas, o aviso non di cantas se cancelan. */
  readonly sessions?: readonly string[];
}

/**
 * «Rematar a tempada»: desde que día (de hoxe á última data; por defecto, hoxe ou o
 * primeiro día se aínda non empezou), o que pasa nunha frase (con cantas sesións se cancelan desde
 * ese día) e «Rematar a tempada» en vermello.
 * Remata coa capa ocupada e pecha coa tempada xa rematada; un erro queda dentro.
 */
@Component({
  selector: 'furar-end-series-dialog',
  imports: [
    ButtonDirective,
    DateFieldComponent,
    DialogComponent,
    FieldComponent,
    IconComponent,
    ProblemAlertComponent,
    ReactiveFormsModule,
    TranslocoPipe,
  ],
  providers: [SeasonsStore],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @let t = 'booking.seasons.end.';
    <furar-dialog
      size="sm"
      [heading]="t + 'title' | transloco"
      [subheading]="data.series.organizationName"
      [busy]="busy()"
    >
      <form novalidate class="gap-form grid" id="furar-end-series" (submit)="$event.preventDefault(); confirm()">
        <furar-field fieldId="furar-end-series-from" [label]="t + 'from' | transloco" required [error]="error()">
          <furar-date-field
            required
            data-end-from
            buttonId="furar-end-series-from"
            [formControl]="from"
            [label]="t + 'from' | transloco"
            [min]="min"
            [max]="data.series.lastDate"
            [today]="today"
            [invalid]="!!error()"
            [describedBy]="error() ? 'furar-end-series-from-error' : null"
          />
        </furar-field>
        <p class="f-season-note" data-end-text>{{ t + text().key | transloco: { count: text().count } }}</p>
        <furar-problem-alert [problem]="problem()" />
      </form>
      <ng-container dialogActions>
        <button type="button" furarButton="quiet" data-end-keep (click)="ref.close()">
          {{ 'common.cancel' | transloco }}
        </button>
        <button type="submit" form="furar-end-series" furarButton="danger" data-end-confirm [busy]="busy()">
          <furar-icon name="event-busy" />
          {{ t + 'confirm' | transloco }}
        </button>
      </ng-container>
    </furar-dialog>
  `,
})
export class EndSeriesDialogComponent {
  protected readonly data = inject<EndSeriesData>(DIALOG_DATA);
  protected readonly ref = inject<DialogRef<SeriesResponse>>(DialogRef);
  private readonly store = inject(SeasonsStore);

  protected readonly today = madridToday();
  /** Desde hoxe ou, se aínda non empezou, desde o primeiro día. */
  protected readonly min: IsoDate = this.data.series.firstDate > this.today ? this.data.series.firstDate : this.today;
  protected readonly from = new FormControl<IsoDate | null>(this.min, Validators.required);
  protected readonly busy = signal(false);
  protected readonly submitted = signal(false);
  protected readonly problem = signal<ApiProblem | null>(null);

  private readonly chosen = toSignal(this.from.valueChanges, { initialValue: this.from.value });
  /** O aviso: con cantas sesións se cancelan desde o día escollido, se se saben. */
  protected readonly text = computed(() => {
    const from = this.chosen();
    const sessions = this.data.sessions;
    if (!from || !sessions) return { key: 'text', count: 0 };
    const count = sessions.filter((start) => inMadrid(start).date >= from).length;
    return { key: count === 1 ? 'textOne' : count ? 'textCount' : 'textNone', count };
  });

  protected error() {
    return fieldError(this.from, this.submitted());
  }

  protected async confirm(): Promise<void> {
    this.submitted.set(true);
    this.problem.set(null);
    const from = this.from.value;
    if (!from || this.busy()) return;
    this.busy.set(true);
    try {
      this.ref.close(await this.store.end(this.data.slug, this.data.series, from));
    } catch (error) {
      this.problem.set(asProblem(error));
    } finally {
      this.busy.set(false);
    }
  }
}
