// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { DOCUMENT } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
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
import { StaffAccessService } from '../../core/auth/staff-access.service';
import { LanguageService } from '../../core/i18n/language.service';
import { hasAgeLimit, shortDay } from '../../shared/events/event-format';
import { ButtonDirective } from '../../shared/ui/button/button.directive';
import { inMadrid } from '../../shared/ui/calendar/madrid-time';
import type { ColumnFilterOption } from '../../shared/ui/column-filter/column-filter.component';
import { ColumnFilterComponent } from '../../shared/ui/column-filter/column-filter.component';
import { IconComponent } from '../../shared/ui/icon/icon.component';
import type { ChipMenuOption } from '../../shared/ui/menu/chip-menu.component';
import { ChipMenuComponent } from '../../shared/ui/menu/chip-menu.component';
import { ProblemAlertComponent } from '../../shared/ui/problem-alert.component';
import type { PersonRow, WaitRow } from './event-people';
import { EventWaitlistComponent } from './event-waitlist.component';
import { PAYMENTS, PAYMENT_TONE, peopleRows, sortByAbsences, waitRows } from './event-people';
import type {
  EventAttendanceSummaryResponse,
  EventPayment,
  EventRegistrationResponse,
  EventResponse,
} from './events.store';
import { EventsStore } from './events.store';

/** Por debaixo disto, unha tarxeta por persoa (rótulo e valor) no canto da táboa. */
const DESKTOP_QUERY = '(min-width: 40rem)';

/**
 * As persoas dun evento (pestana «Persoas» do detalle): as solicitudes pendentes, se é «Con
 * aprobación», con «Rexeitar» · «Aceptar» (só Xestión); as apuntadas nunha táboa (Persoa, A cargo
 * de, Nacemento, Faltas e Pago, cada columna só se ten sentido: persoas a cargo, límite de idade,
 * actividade con horario, prezo), co pago editable en `furar-chip-menu` e filtro na súa cabeceira
 * e as faltas ordenables; e a lista de espera (Posto, Persoa, Estado: «Avisada · só ela ata as
 * 18:42» mentres dura a súa hora). No móbil, unha tarxeta por persoa con rótulo e valor.
 */
@Component({
  selector: 'furar-event-people',
  imports: [
    ButtonDirective,
    ChipMenuComponent,
    ColumnFilterComponent,
    IconComponent,
    ProblemAlertComponent,
    EventWaitlistComponent,
    TranslocoPipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'f-event-people' },
  templateUrl: './event-people.component.html',
})
export class EventPeopleComponent {
  private readonly store = inject(EventsStore);
  private readonly transloco = inject(TranslocoService);
  private readonly language = inject(LanguageService);
  private readonly staff = inject(StaffAccessService);
  private readonly view = inject(DOCUMENT).defaultView;

  readonly slug = input.required<string>();
  readonly event = input.required<EventResponse>();
  /** Aceptouse ou rexeitouse unha solicitude (cambian os números do evento). */
  readonly changed = output();

  protected readonly people = signal<readonly EventRegistrationResponse[]>([]);
  protected readonly summary = signal<readonly EventAttendanceSummaryResponse[]>([]);
  protected readonly busy = signal<string | null>(null);
  protected readonly problem = signal<ApiProblem | null>(null);
  protected readonly desktop = signal(this.view?.matchMedia?.(DESKTOP_QUERY).matches ?? true);
  protected readonly payments = signal<readonly string[]>([]);
  protected readonly sort = signal<'' | 'desc' | 'asc'>('');
  /** Aceptar e rexeitar: só Xestión (Recepción ve as solicitudes). */
  protected readonly reviewer = computed(() => this.staff.role() === 'MANAGER');
  protected readonly scheduled = computed(() => this.event().kind === 'SCHEDULED');
  protected readonly requests = computed(() => this.people().filter((p) => p.status === 'PENDING'));
  private readonly allRows = computed(() => {
    this.language.lang();
    return peopleRows(this.people(), this.summary(), (key, params) => this.transloco.translate(key, params));
  });
  protected readonly rows = computed<PersonRow[]>(() => {
    const filter = this.payments();
    const rows = this.allRows().filter((row) => !filter.length || (row.payment && filter.includes(row.payment)));
    return sortByAbsences(rows, this.sort());
  });
  protected readonly waits = computed<WaitRow[]>(() => {
    this.language.lang();
    return waitRows(this.people(), (key, params) => this.transloco.translate(key, params));
  });
  /** As columnas que teñen sentido neste evento. */
  protected readonly columns = computed(() => {
    const rows = this.allRows();
    const event = this.event();
    return {
      dependent: rows.some((row) => row.dependent),
      born: hasAgeLimit(event),
      absences: this.scheduled(),
      payment: rows.some((row) => row.payment),
    };
  });
  protected readonly paymentOptions = computed<ChipMenuOption<EventPayment>[]>(() => {
    this.language.lang();
    return PAYMENTS.map((value) => ({
      value,
      label: this.transloco.translate('event.payment.' + value),
      tone: PAYMENT_TONE[value],
    }));
  });
  protected readonly paymentFilter = computed<ColumnFilterOption[]>(() => {
    const rows = this.allRows();
    return this.paymentOptions().map((option) => ({
      ...option,
      count: rows.filter((row) => row.payment === option.value).length,
    }));
  });
  protected readonly sortOptions = computed<ColumnFilterOption[]>(() => {
    this.language.lang();
    return (['desc', 'asc'] as const).map((value) => ({
      value,
      label: this.transloco.translate('event.people.sort.' + value),
    }));
  });

  /** Só o id: o evento cambia de obxecto ao aceptar (os números), pero as persoas xa están. */
  private readonly eventId = computed(() => this.event().id);

  constructor() {
    effect(() => {
      const slug = this.slug();
      const id = this.eventId();
      untracked(() => void this.load(slug, id));
    });
    const query = this.view?.matchMedia?.(DESKTOP_QUERY);
    const onChange = (event: MediaQueryListEvent) => this.desktop.set(event.matches);
    query?.addEventListener?.('change', onChange);
    inject(DestroyRef).onDestroy(() => query?.removeEventListener?.('change', onChange));
  }

  protected who(person: EventRegistrationResponse): string {
    return person.dependentName ?? person.name ?? person.email ?? this.transloco.translate('event.people.anonymized');
  }

  protected day(instant: string): string {
    return shortDay(inMadrid(instant).date, this.language.lang());
  }

  protected togglePayment(value: string): void {
    this.payments.update((list) => (list.includes(value) ? list.filter((v) => v !== value) : [...list, value]));
  }

  protected sortBy(value: string): void {
    this.sort.update((current) => (current === value ? '' : (value as 'desc' | 'asc')));
  }

  protected accept(person: EventRegistrationResponse): Promise<void> {
    return this.act(person.id + 'accept', async () => {
      const saved = await this.store.accept(this.slug(), this.event().id, person.id);
      this.replace(saved);
      this.changed.emit();
    });
  }

  protected reject(person: EventRegistrationResponse): Promise<void> {
    return this.act(person.id + 'reject', async () => {
      const saved = await this.store.reject(this.slug(), this.event().id, person.id);
      this.replace(saved);
      this.changed.emit();
    });
  }

  protected pay(row: PersonRow, status: EventPayment): Promise<void> {
    return this.act(row.id + 'pay', async () => {
      this.replace(await this.store.pay(this.slug(), this.event().id, row.id, status));
    });
  }

  private async act(key: string, work: () => Promise<void>): Promise<void> {
    if (this.busy()) return;
    this.busy.set(key);
    this.problem.set(null);
    try {
      await work();
    } catch (error) {
      this.problem.set(error as ApiProblem);
      await this.load(this.slug(), this.event().id);
    } finally {
      this.busy.set(null);
    }
  }

  private replace(saved: EventRegistrationResponse): void {
    this.people.update((people) => people.map((p) => (p.id === saved.id ? saved : p)));
  }

  private async load(slug: string, id: string): Promise<void> {
    try {
      const [people, summary] = await Promise.all([
        this.store.registrations(slug, id),
        this.scheduled() ? this.store.attendanceSummary(slug, id) : Promise.resolve([]),
      ]);
      this.people.set(people);
      this.summary.set(summary);
    } catch (error) {
      this.people.set([]);
      this.problem.set(error as ApiProblem);
    }
  }
}
