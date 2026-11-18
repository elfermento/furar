// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { TranslocoPipe } from '@jsverse/transloco';
import type { ApiProblem } from '../../core/errors/api-problem';
import { asProblem } from '../../core/errors/problem.interceptor';
import { ButtonDirective } from '../../shared/ui/button/button.directive';
import type { IsoDate } from '../../shared/ui/calendar/local-date';
import { inMadrid, madridToday } from '../../shared/ui/calendar/madrid-time';
import { DateFieldComponent } from '../../shared/ui/date-field/date-field.component';
import { DialogComponent } from '../../shared/ui/dialog/dialog.component';
import { fieldError } from '../../shared/ui/field-errors';
import { FieldComponent } from '../../shared/ui/field.component';
import { IconComponent } from '../../shared/ui/icon/icon.component';
import { ProblemAlertComponent } from '../../shared/ui/problem-alert.component';
import type { EventResponse, EventSessionResponse } from './events.store';
import { EventsStore } from './events.store';

/** A actividade que se remata, o seu nome, o concello e as sesións que ten. */
export interface EndActivityData {
  readonly slug: string;
  readonly event: EventResponse;
  readonly name: string;
  readonly sessions: readonly EventSessionResponse[];
}

/**
 * «Rematar a actividade», coma «Rematar a tempada»: desde que día (de hoxe á última data; por
 * defecto, hoxe ou o primeiro día se aínda non empezou), cantas sesións se cancelan desde ese día
 * (avísase por correo ás persoas apuntadas) e «Rematar a actividade» en vermello. Pecha coa
 * actividade xa rematada; un erro queda dentro.
 */
@Component({
  selector: 'furar-end-activity-dialog',
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
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @let t = 'event.end.';
    <furar-dialog size="sm" [heading]="t + 'title' | transloco" [subheading]="data.name" [busy]="busy()">
      <form novalidate class="gap-form grid" id="furar-end-activity" (submit)="$event.preventDefault(); confirm()">
        <furar-field fieldId="furar-end-activity-from" [label]="t + 'from' | transloco" required [error]="error()">
          <furar-date-field
            required
            data-end-from
            buttonId="furar-end-activity-from"
            [formControl]="from"
            [label]="t + 'from' | transloco"
            [min]="min"
            [max]="last"
            [today]="today"
            [invalid]="!!error()"
            [describedBy]="error() ? 'furar-end-activity-from-error' : null"
          />
        </furar-field>
        <p class="f-season-note" data-end-text>{{ t + text().key | transloco: { count: text().count } }}</p>
        <furar-problem-alert [problem]="problem()" />
      </form>
      <ng-container dialogActions>
        <button type="button" furarButton="quiet" data-end-keep (click)="ref.close()">
          {{ 'common.cancel' | transloco }}
        </button>
        <button type="submit" form="furar-end-activity" furarButton="danger" data-end-confirm [busy]="busy()">
          <furar-icon name="event-busy" />
          {{ t + 'confirm' | transloco }}
        </button>
      </ng-container>
    </furar-dialog>
  `,
})
export class EndActivityDialogComponent {
  protected readonly data = inject<EndActivityData>(DIALOG_DATA);
  protected readonly ref = inject<DialogRef<EventResponse>>(DialogRef);
  private readonly store = inject(EventsStore);

  protected readonly today = madridToday();
  private readonly first = this.data.event.schedule?.firstDate ?? this.today;
  protected readonly last: IsoDate = this.data.event.schedule?.lastDate ?? this.today;
  /** Desde hoxe ou, se aínda non empezou, desde o primeiro día. */
  protected readonly min: IsoDate = this.first > this.today ? this.first : this.today;
  protected readonly from = new FormControl<IsoDate | null>(this.min, Validators.required);
  protected readonly busy = signal(false);
  protected readonly submitted = signal(false);
  protected readonly problem = signal<ApiProblem | null>(null);

  private readonly chosen = toSignal(this.from.valueChanges, { initialValue: this.from.value });
  /** Cantas sesións sen empezar e sen lista se cancelan desde o día escollido. */
  protected readonly text = computed(() => {
    const from = this.chosen();
    if (!from) return { key: 'textNone', count: 0 };
    const now = Date.now();
    const count = this.data.sessions.filter(
      (s) =>
        !s.cancelledAt &&
        !s.attendanceTaken &&
        new Date(s.startsAt).getTime() > now &&
        inMadrid(s.startsAt).date >= from,
    ).length;
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
      this.ref.close(await this.store.end(this.data.slug, this.data.event.id, from));
    } catch (error) {
      this.problem.set(asProblem(error));
    } finally {
      this.busy.set(false);
    }
  }
}
