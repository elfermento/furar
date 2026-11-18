// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, computed, inject, input, output } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { LanguageService } from '../../core/i18n/language.service';
import { ButtonDirective } from '../ui/button/button.directive';
import { IconComponent } from '../ui/icon/icon.component';
import type { ScheduleConflict, SchedulePreview } from './schedule';
import { conflictDates, conflictText, conflictWhen, datesText } from './schedule';

/** Un choque xa pintado: o día, o que hai e, se é unha reserva ou unha sesión, o seu botón. */
interface Row {
  readonly key: string;
  readonly when: string;
  readonly what: string;
  readonly conflict: ScheduleConflict;
  readonly action: 'booking' | 'session' | null;
}

/**
 * Os días que non se crean dun horario (unha tempada ou unha actividade): no aviso, «N días xa están ocupados. Eses
 * días non se crean.» e cada choque co seu día e hora e o que hai («Reserva de …», «Sesión de …»,
 * «Bloqueo: …»); nas reservas e nas sesións, «Cancelar a reserva» ou «Cancelar a sesión» á
 * dereita, na súa liña (`cancelBooking`). Debaixo, nunha liña cada un, os festivos, os días sen o uso e
 * os pechados: o primeiro aviso que sae «non se crean» e os que van detrás doutro, «tampouco».
 */
@Component({
  selector: 'furar-schedule-preview',
  imports: [ButtonDirective, IconComponent, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @let t = 'booking.seasons.preview.';
    @if (rows().length) {
      <section class="f-season-clashes" data-season-conflicts [attr.aria-label]="heading()">
        <p class="f-season-clashes__head">
          <furar-icon name="warn" />
          <b>{{ heading() }}</b>
        </p>
        <ul class="f-season-clashes__list">
          @for (row of rows(); track row.key) {
            <li class="f-season-clashes__item" [attr.data-conflict]="row.conflict.kind">
              <p class="f-season-clashes__text">
                <b>{{ row.when }}</b>
                <span>{{ row.what }}</span>
              </p>
              @if (row.action; as action) {
                <div class="f-season-clashes__act">
                  <button
                    type="button"
                    furarButton="danger"
                    size="sm"
                    [attr.data-conflict-cancel]="row.conflict.bookingId"
                    (click)="cancelBooking.emit(row.conflict)"
                  >
                    <furar-icon name="event-busy" />
                    {{ (action === 'session' ? t + 'cancelSession' : t + 'cancelBooking') | transloco }}
                  </button>
                </div>
              }
            </li>
          }
        </ul>
      </section>
    }
    @for (line of lines(); track line.key) {
      <p class="f-season-note" [attr.data-skipped]="line.key">{{ line.text }}</p>
    }
  `,
})
export class SchedulePreviewComponent {
  private readonly language = inject(LanguageService);
  private readonly transloco = inject(TranslocoService);
  private readonly langChanges = toSignal(this.transloco.langChanges$);

  readonly preview = input.required<SchedulePreview>();
  /** «Cancelar a reserva» ou «Cancelar a sesión» dun choque. */
  readonly cancelBooking = output<ScheduleConflict>();

  private readonly t = (key: string, params?: Record<string, unknown>) => this.transloco.translate(key, params);

  protected readonly heading = computed(() => {
    this.langChanges();
    const count = conflictDates(this.preview()).length;
    return this.t(`booking.seasons.preview.${count === 1 ? 'busyOne' : 'busyOther'}`, { count });
  });

  protected readonly rows = computed<Row[]>(() => {
    this.langChanges();
    const lang = this.language.lang();
    return this.preview().conflicts.map((conflict, index) => ({
      key: `${conflict.date}-${index}`,
      when: conflictWhen(conflict, lang, this.t),
      what: conflictText(conflict, this.t),
      conflict,
      action: !conflict.bookingId
        ? null
        : conflict.kind === 'SESSION'
          ? 'session'
          : conflict.kind === 'BOOKING'
            ? 'booking'
            : null,
    }));
  });

  /** Os días que non se crean por festivos, polo uso ou por estar pechado. */
  protected readonly lines = computed(() => {
    this.langChanges();
    const lang = this.language.lang();
    const preview = this.preview();
    const groups: [string, readonly string[]][] = [
      ['holidays', preview.holidays],
      ['useOff', preview.useOff],
      ['closed', preview.closed],
    ];
    // «Tampouco» só se antes saíu outro aviso (os ocupados ou a liña anterior).
    const after = this.rows().length > 0;
    return groups
      .filter(([, dates]) => dates.length > 0)
      .map(([key, dates], index) => ({
        key,
        text: this.t(`booking.seasons.preview.${key}${after || index > 0 ? 'Too' : ''}`, {
          dates: datesText(dates, lang, this.t),
        }),
      }));
  });
}
