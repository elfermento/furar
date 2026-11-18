// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, computed, effect, inject, signal, untracked } from '@angular/core';
import { Router } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { StaffAccessService } from '../../../core/auth/staff-access.service';
import { LanguageService } from '../../../core/i18n/language.service';
import { CurrentMunicipality } from '../../../core/municipality/current-municipality';
import { inLang } from '../../../shared/events/event-format';
import { inMadrid } from '../../../shared/ui/calendar/madrid-time';
import type { StatusCardLine } from '../../../shared/ui/status-card/status-card.component';
import { StatusCardComponent } from '../../../shared/ui/status-card/status-card.component';
import { TagComponent } from '../../../shared/ui/tag/tag.component';
import type { EventTodaySessionResponse } from './today-activities.store';
import { TodayActivitiesStore } from './today-activities.store';

/** Unha sesión de hoxe xa pintada. */
interface Card {
  readonly session: EventTodaySessionResponse;
  readonly name: string;
  readonly lines: StatusCardLine[];
}

/**
 * «Actividades de hoxe» debaixo do validador (ADR-0041, punto 8): unha tarxeta por sesión
 * (nome, horas, lugar e persoas apuntadas), pulsable enteira, que abre «Pasar lista». Primeiro as
 * que están sen pasar (chip ámbar «Sen pasar»), despois as pasadas (chip verde «Lista pasada · 18 de 20»). Sen
 * sesións hoxe (ou sen `event.attendance`), o bloque non sae.
 */
@Component({
  selector: 'furar-today-activities',
  imports: [StatusCardComponent, TagComponent, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'f-today-activities' },
  template: `
    @let t = 'validate.activities.';
    @if (cards().length) {
      <section class="f-today-activities__section" aria-labelledby="today-activities-title" data-today-activities>
        <h2 id="today-activities-title" class="f-today-activities__title">{{ t + 'title' | transloco }}</h2>
        <ul class="f-today-activities__list">
          @for (card of cards(); track card.session.sessionId) {
            <li>
              <furar-status-card
                [attr.data-today-session]="card.session.sessionId"
                [level]="3"
                [heading]="card.name"
                [lines]="card.lines"
                (opened)="open(card.session)"
              >
                @if (card.session.attendanceTaken) {
                  <furar-tag cardChips tone="success" data-chip="taken">{{
                    t + 'takenOf'
                      | transloco: { count: card.session.present, total: card.session.present + card.session.absent }
                  }}</furar-tag>
                } @else {
                  <furar-tag cardChips tone="warn" data-chip="pending">{{ t + 'pending' | transloco }}</furar-tag>
                }
              </furar-status-card>
            </li>
          }
        </ul>
      </section>
    }
  `,
})
export class TodayActivitiesComponent {
  private readonly store = inject(TodayActivitiesStore);
  private readonly router = inject(Router);
  private readonly municipality = inject(CurrentMunicipality);
  private readonly staff = inject(StaffAccessService);
  private readonly language = inject(LanguageService);
  private readonly transloco = inject(TranslocoService);

  private readonly sessions = signal<readonly EventTodaySessionResponse[]>([]);
  /** `event.attendance`: Xestión e Recepción. */
  private readonly allowed = computed(() => this.staff.role() === 'MANAGER' || this.staff.role() === 'STAFF');

  protected readonly cards = computed<Card[]>(() => {
    const lang = this.language.lang();
    return [...this.sessions()]
      .sort((a, b) => Number(a.attendanceTaken) - Number(b.attendanceTaken) || a.startsAt.localeCompare(b.startsAt))
      .map((session) => ({
        session,
        name: inLang(session.name, lang),
        lines: [
          {
            id: 'when',
            parts: [
              `${inMadrid(session.startsAt).time}–${inMadrid(session.endsAt).time}`,
              session.facility?.name ?? session.place ?? '',
              this.transloco.translate('validate.activities.registered', { count: session.registered }),
            ].filter(Boolean),
          },
        ],
      }));
  });

  constructor() {
    effect(() => {
      const slug = this.municipality.slug();
      if (slug && this.allowed()) untracked(() => void this.load(slug));
    });
  }

  protected open(session: EventTodaySessionResponse): void {
    const slug = this.municipality.slug() ?? '';
    void this.router.navigate(['/', slug, 'xestion', 'eventos', session.eventId, 'sesions', session.sessionId], {
      queryParams: { desde: 'validar' },
    });
  }

  private async load(slug: string): Promise<void> {
    try {
      this.sessions.set(await this.store.today(slug));
    } catch {
      // Sen as sesións, o validador segue igual: o bloque non sae.
      this.sessions.set([]);
    }
  }
}
