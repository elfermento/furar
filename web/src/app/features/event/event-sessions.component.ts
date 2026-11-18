// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import {
  ChangeDetectionStrategy,
  Component,
  booleanAttribute,
  computed,
  effect,
  inject,
  input,
  output,
  signal,
  untracked,
} from '@angular/core';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import type { ApiProblem } from '../../core/errors/api-problem';
import { asProblem } from '../../core/errors/problem.interceptor';
import { LanguageService } from '../../core/i18n/language.service';
import { capitalized } from '../../shared/schedule/schedule';
import { longDay } from '../../shared/events/event-format';
import { ButtonDirective } from '../../shared/ui/button/button.directive';
import { inMadrid, madridToday } from '../../shared/ui/calendar/madrid-time';
import { DialogService } from '../../shared/ui/dialog/dialog.service';
import { IconComponent } from '../../shared/ui/icon/icon.component';
import { MenuDirective, MenuItemDirective, MenuTriggerDirective } from '../../shared/ui/menu/menu.directives';
import { ProblemAlertComponent } from '../../shared/ui/problem-alert.component';
import { StatusStateComponent } from '../../shared/ui/status-state/status-state.component';
import { TagComponent } from '../../shared/ui/tag/tag.component';
import { ToastService } from '../../shared/ui/toast/toast.service';
import type { SessionRow } from './event-sessions';
import { SESSIONS_SHOWN, sessionRows, sessionWindow } from './event-sessions';
import type { EventResponse, EventSessionResponse } from './events.store';
import { EventsStore } from './events.store';

/**
 * As sesións dunha actividade (pestana «Sesións» do detalle): unha fila por día coa data, a
 * asistencia («Asistencia: 18 de 20») ou as horas e, na súa columna, o chip do estado («Hoxe»,
 * «Cancelada»). A de hoxe e as pasadas púlsanse enteiras e abren «Pasar lista» (ou corrixila); no
 * menú «⋯» das que veñen, «Cancelar a sesión» (avisa por correo ás persoas apuntadas). Nunca un
 * botón xunto a un chip. Vense a última pasada e as seguintes; «Ver todas», o resto.
 */
@Component({
  selector: 'furar-event-sessions',
  imports: [
    ButtonDirective,
    IconComponent,
    MenuDirective,
    MenuItemDirective,
    MenuTriggerDirective,
    ProblemAlertComponent,
    StatusStateComponent,
    TagComponent,
    TranslocoPipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'f-event-sessions' },
  template: `
    @let t = 'event.sessions.';
    @switch (state()) {
      @case ('loading') {
        <furar-status-state mode="skeleton" shape="row" [rows]="4" [description]="t + 'loading' | transloco" />
      }
      @case ('ready') {
        @if (rows().length) {
          <ul
            class="f-card f-event-sessions__list"
            data-sessions
            [attr.aria-label]="'event.detail.tab.sessions' | transloco"
          >
            @for (row of shown(); track row.session.id) {
              @let open = canAttend() && (row.state === 'today' || row.past);
              <li
                class="f-event-sessions__row"
                [class.f-event-sessions__row--open]="open"
                [attr.data-session]="row.session.id"
                [attr.data-state]="row.state"
              >
                <div class="f-event-sessions__text">
                  @if (open) {
                    <!-- A fila enteira abre «Pasar lista» (o botón estírase sobre ela). -->
                    <button
                      type="button"
                      class="f-event-sessions__open"
                      data-session-attend
                      (click)="attend.emit(row.session)"
                    >
                      {{ day(row) }}
                    </button>
                  } @else {
                    <b>{{ day(row) }}</b>
                  }
                  <span class="f-event-sessions__meta tabular-nums">
                    @if (row.session.attendanceTaken) {
                      {{
                        t + 'attendance'
                          | transloco: { count: row.session.present, total: row.session.present + row.session.absent }
                      }}
                    } @else {
                      {{ hours(row.session) }}
                    }
                  </span>
                </div>
                <div class="f-event-sessions__chip">
                  @if (row.state === 'cancelled') {
                    <furar-tag tone="danger" data-session-chip>{{ t + 'cancelled' | transloco }}</furar-tag>
                  } @else if (row.state === 'today') {
                    <furar-tag tone="success" data-session-chip>{{ t + 'today' | transloco }}</furar-tag>
                  }
                </div>
                <div class="f-event-sessions__acts">
                  @if (row.cancellable && canManage()) {
                    <button
                      type="button"
                      furarButton
                      round
                      [furarMenuTrigger]="menu"
                      [attr.data-session-more]="row.session.id"
                      [attr.aria-label]="t + 'actionsFor' | transloco: { day: day(row) }"
                    >
                      <furar-icon name="more" />
                    </button>
                    <ng-template #menu>
                      <div furarMenu [attr.aria-label]="t + 'actionsFor' | transloco: { day: day(row) }">
                        <button type="button" furarMenuItem data-session-cancel (triggered)="cancel(row)">
                          <furar-icon name="event-busy" />
                          {{ t + 'cancel' | transloco }}
                        </button>
                      </div>
                    </ng-template>
                  } @else if (open) {
                    <furar-icon class="f-event-sessions__chevron" name="chevron-right" />
                  }
                </div>
              </li>
            }
          </ul>
          @if (hidden() > 0) {
            <div class="f-event-sessions__more">
              <button type="button" furarButton data-sessions-all (click)="all.set(true)">
                {{ t + 'all' | transloco: { count: rows().length } }}
              </button>
            </div>
          }
        } @else {
          <p class="text-ink-soft" data-sessions-empty>{{ t + 'empty' | transloco }}</p>
        }
      }
    }
    <furar-problem-alert [problem]="problem()" />
  `,
})
export class EventSessionsComponent {
  private readonly store = inject(EventsStore);
  private readonly dialogs = inject(DialogService);
  private readonly toasts = inject(ToastService);
  private readonly transloco = inject(TranslocoService);
  private readonly language = inject(LanguageService);

  readonly slug = input.required<string>();
  readonly event = input.required<EventResponse>();
  /** Cancelar sesións (`event.manage`). */
  readonly canManage = input(false, { transform: booleanAttribute });
  /** Pasar lista (`event.attendance`). */
  readonly canAttend = input(false, { transform: booleanAttribute });
  /** «Pasar lista» dunha sesión. */
  readonly attend = output<EventSessionResponse>();
  /** As sesións que hai (para «Rematar a actividade»). */
  readonly loaded = output<readonly EventSessionResponse[]>();

  protected readonly sessions = signal<readonly EventSessionResponse[]>([]);
  protected readonly state = signal<'loading' | 'ready'>('loading');
  protected readonly problem = signal<ApiProblem | null>(null);
  protected readonly all = signal(false);
  protected readonly rows = computed<SessionRow[]>(() => sessionRows(this.sessions(), madridToday(), new Date()));
  protected readonly shown = computed(() =>
    this.all() ? this.rows() : sessionWindow(this.rows(), SESSIONS_SHOWN, madridToday()),
  );
  protected readonly hidden = computed(() => this.rows().length - this.shown().length);
  private readonly key = computed(() => `${this.event().id}:${this.event().version}`);

  constructor() {
    effect(() => {
      this.key();
      untracked(() => void this.reload());
    });
  }

  async reload(): Promise<void> {
    try {
      const sessions = await this.store.sessions(this.slug(), this.event().id);
      this.sessions.set(sessions);
      this.loaded.emit(sessions);
    } catch (error) {
      this.problem.set(asProblem(error));
    } finally {
      this.state.set('ready');
    }
  }

  protected day(row: SessionRow): string {
    const lang = this.language.lang();
    return capitalized(longDay(row.session.date, lang), lang);
  }

  protected hours(session: EventSessionResponse): string {
    return `${inMadrid(session.startsAt).time}–${inMadrid(session.endsAt).time}`;
  }

  protected async cancel(row: SessionRow): Promise<void> {
    const t = (key: string, params?: Record<string, unknown>) => this.transloco.translate(key, params);
    const event = this.event();
    this.problem.set(null);
    try {
      const done = await this.dialogs.confirm({
        heading: t('event.sessions.cancelConfirm.title'),
        message: t(event.confirmed ? 'event.sessions.cancelConfirm.text' : 'event.sessions.cancelConfirm.nobody', {
          day: this.day(row),
          count: event.confirmed,
        }),
        confirmLabel: t('event.sessions.cancel'),
        tone: 'danger',
        action: () => this.store.cancelSession(this.slug(), event.id, row.session.id),
      });
      if (done) {
        this.toasts.show('event.sessions.cancelledToast');
        await this.reload();
      }
    } catch (error) {
      this.problem.set(asProblem(error));
    }
  }
}
