// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, computed, effect, inject, signal, untracked } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { asProblem } from '../../core/errors/problem.interceptor';
import { LanguageService } from '../../core/i18n/language.service';
import { CurrentMunicipality } from '../../core/municipality/current-municipality';
import { inLang, longDay } from '../../shared/events/event-format';
import { capitalized } from '../../shared/schedule/schedule';
import { ActionBarComponent } from '../../shared/ui/action-bar/action-bar.component';
import { ButtonDirective } from '../../shared/ui/button/button.directive';
import { inMadrid } from '../../shared/ui/calendar/madrid-time';
import { IconComponent } from '../../shared/ui/icon/icon.component';
import { StatusStateComponent } from '../../shared/ui/status-state/status-state.component';
import { SwitchComponent } from '../../shared/ui/switch/switch.component';
import { ToastService } from '../../shared/ui/toast/toast.service';
import type { EventAttendanceResponse, EventResponse } from './events.store';
import { EventsStore } from './events.store';

/** Unha persoa da lista, co seu interruptor (presente ou non). */
interface Row {
  readonly id: string;
  readonly name: string;
  readonly present: FormControl<boolean>;
}

/**
 * «Pasar lista» dunha sesión (ADR-0041, punto 8), pensada para o móbil: a actividade, o día e a
 * hora; unha fila por persoa co seu nome e o interruptor á dereita (todas presentes ao abrir unha
 * sesión sen lista; o persoal apaga as que faltan); e a barra de accións con «18 de 20 presentes»
 * e «Gardar» (ou «Gardar cambios», se xa se pasou). Ao gardar, aviso flotante. Volve a «Validar QR»
 * (`?desde=validar`) ou ao detalle da actividade.
 */
@Component({
  selector: 'furar-attendance-page',
  imports: [
    ActionBarComponent,
    ButtonDirective,
    IconComponent,
    ReactiveFormsModule,
    RouterLink,
    StatusStateComponent,
    SwitchComponent,
    TranslocoPipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @let t = 'event.attendance.';
    <div class="f-attendance">
      <div class="f-reserve__title">
        <a
          furarButton="secondary"
          round
          data-attendance-back
          [routerLink]="back().link"
          [queryParams]="back().query"
          [attr.aria-label]="t + 'back' | transloco"
        >
          <furar-icon name="arrow-left" />
        </a>
        <h1>{{ t + 'title' | transloco }}</h1>
      </div>
      @switch (state()) {
        @case ('loading') {
          <furar-status-state mode="skeleton" shape="row" [rows]="6" [description]="t + 'loading' | transloco" />
        }
        @case ('error') {
          <furar-status-state mode="error" [heading]="t + 'loadError' | transloco" [description]="error()">
            <button type="button" furarButton (click)="load()">{{ t + 'retry' | transloco }}</button>
          </furar-status-state>
        }
        @case ('ready') {
          <p class="f-attendance__what" data-attendance-what>
            <b>{{ name() }}</b>
            <span>{{ when() }}</span>
          </p>
          @if (takenText(); as taken) {
            <p class="f-attendance__taken" data-attendance-taken>{{ taken }}</p>
          }
          @if (rows().length) {
            <ul class="f-card f-attendance__list" data-attendance-list [attr.aria-label]="t + 'list' | transloco">
              @for (row of rows(); track row.id) {
                <li class="f-attendance__row" [attr.data-attendee]="row.id">
                  <furar-switch [formControl]="row.present" [label]="row.name" />
                </li>
              }
            </ul>
            <furar-action-bar [label]="t + 'title' | transloco">
              <span actionBarStatus class="f-attendance__count" role="status" data-attendance-count>
                <b class="tabular-nums">{{ t + 'countOf' | transloco: { count: present(), total: rows().length } }}</b>
                {{ t + 'present' | transloco }}
              </span>
              <button type="button" furarButton="primary" data-attendance-save [busy]="saving()" (click)="save()">
                {{ (saving() ? 'common.saving' : taken() ? t + 'saveChanges' : 'common.save') | transloco }}
              </button>
            </furar-action-bar>
          } @else {
            <furar-status-state mode="empty" data-attendance-empty [heading]="t + 'empty' | transloco" />
          }
        }
      }
    </div>
  `,
})
export class AttendancePage {
  private readonly store = inject(EventsStore);
  private readonly route = inject(ActivatedRoute);
  private readonly municipality = inject(CurrentMunicipality);
  private readonly language = inject(LanguageService);
  private readonly toasts = inject(ToastService);
  private readonly transloco = inject(TranslocoService);

  private readonly eventId = this.route.snapshot.paramMap.get('id') ?? '';
  private readonly sessionId = this.route.snapshot.paramMap.get('sessionId') ?? '';
  private readonly from = this.route.snapshot.queryParamMap.get('desde');

  protected readonly state = signal<'loading' | 'ready' | 'error'>('loading');
  protected readonly error = signal('');
  protected readonly event = signal<EventResponse | null>(null);
  protected readonly attendance = signal<EventAttendanceResponse | null>(null);
  protected readonly rows = signal<readonly Row[]>([]);
  protected readonly saving = signal(false);
  private readonly changes = signal(0);

  protected readonly taken = computed(() => this.attendance()?.taken ?? false);
  protected readonly present = computed(() => {
    this.changes();
    return this.rows().filter((row) => row.present.value).length;
  });
  protected readonly name = computed(() => {
    const event = this.event();
    return event ? inLang(event.name, this.language.lang()) : '';
  });
  /** «Mércores, 18 de novembro · 17:00–18:00». */
  protected readonly when = computed(() => {
    const attendance = this.attendance();
    if (!attendance) return '';
    const lang = this.language.lang();
    const day = capitalized(longDay(attendance.date, lang), lang);
    return `${day} · ${inMadrid(attendance.startsAt).time}–${inMadrid(attendance.endsAt).time}`;
  });
  /** «Pasada ás 17:10 por Xestora Narón» (sen nome, só a hora). */
  protected readonly takenText = computed(() => {
    this.language.lang();
    const attendance = this.attendance();
    if (!attendance?.taken || !attendance.takenAt) return null;
    const time = inMadrid(attendance.takenAt).time;
    return attendance.takenByName
      ? this.transloco.translate('event.attendance.takenBy', { time, name: attendance.takenByName })
      : this.transloco.translate('event.attendance.takenAt', { time });
  });
  protected readonly back = computed(() => {
    const slug = this.municipality.slug() ?? '';
    return this.from === 'validar'
      ? { link: ['/', slug, 'xestion', 'validar'], query: {} }
      : { link: ['/', slug, 'xestion', 'eventos'], query: { ver: this.eventId } };
  });

  constructor() {
    effect(() => {
      if (this.municipality.slug()) untracked(() => void this.load());
    });
  }

  protected async load(): Promise<void> {
    const slug = this.municipality.slug();
    if (!slug) return;
    this.state.set('loading');
    try {
      const [event, attendance] = await Promise.all([
        this.store.get(slug, this.eventId),
        this.store.attendance(slug, this.eventId, this.sessionId),
      ]);
      this.event.set(event);
      this.show(attendance);
      this.state.set('ready');
    } catch (failure) {
      this.error.set(asProblem(failure).translated);
      this.state.set('error');
    }
  }

  protected async save(): Promise<void> {
    const slug = this.municipality.slug();
    if (!slug || this.saving()) return;
    this.saving.set(true);
    try {
      const marks = this.rows().map((row) => ({ registrationId: row.id, present: row.present.value }));
      const saved = await this.store.saveAttendance(slug, this.eventId, this.sessionId, marks);
      this.show(saved);
      this.toasts.show('event.attendance.saved', { count: this.present(), total: this.rows().length });
    } catch (failure) {
      this.toasts.error('event.attendance.saveError', asProblem(failure));
    } finally {
      this.saving.set(false);
    }
  }

  /** Sen lista gardada, todas presentes. */
  private show(attendance: EventAttendanceResponse): void {
    this.attendance.set(attendance);
    const rows = attendance.attendees
      .map((person) => {
        const present = new FormControl(person.present ?? true, { nonNullable: true });
        present.valueChanges.subscribe(() => this.changes.update((n) => n + 1));
        return {
          id: person.registrationId,
          name: person.dependentName ?? person.name ?? '—',
          present,
        };
      })
      .sort((a, b) => a.name.localeCompare(b.name, 'gl'));
    this.rows.set(rows);
  }
}
