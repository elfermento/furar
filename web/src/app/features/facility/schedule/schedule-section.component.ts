// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  computed,
  effect,
  inject,
  signal,
  untracked,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { LanguageService } from '../../../core/i18n/language.service';
import { ApiProblem } from '../../../core/errors/api-problem';
import { CurrentMunicipality } from '../../../core/municipality/current-municipality';
import { ButtonDirective } from '../../../shared/ui/button/button.directive';
import { EditSession } from '../../../shared/ui/edit-session/edit-session';
import { asProblem } from '../../../shared/ui/edit-section/section-errors';
import { IconComponent } from '../../../shared/ui/icon/icon.component';
import { SegmentedDirective } from '../../../shared/ui/segmented/segmented.directive';
import { StatusStateComponent } from '../../../shared/ui/status-state/status-state.component';
import { TagComponent } from '../../../shared/ui/tag/tag.component';
import { AffectedBookingsFlow } from '../affected-bookings/affected-bookings.flow';
import { FacilityDetailStore } from '../detail/facility-detail.store';
import type { StagedFailure } from '../detail/staged-failures.component';
import { StagedFailuresComponent } from '../detail/staged-failures.component';
import { ExceptionListComponent } from './exceptions/exception-list.component';
import { ExceptionsStore } from './exceptions/exceptions.store';
import { ScheduleGapsComponent } from './exceptions/schedule-gaps.component';
import type { DayHours, Season } from './schedule.store';
import { ScheduleStore } from './schedule.store';
import { SeasonEdits } from './season-edits';
import { SeasonListComponent } from './season-list.component';
import { seasonRange } from './week-format';
import { WeekRowsComponent } from './week-rows.component';

/** Unha tempada gardada tal como se pinta fóra da edición. */
interface SeasonView {
  readonly key: string;
  readonly name: string;
  readonly range: string;
  readonly week: readonly DayHours[];
}

/**
 * Sección «Horario» da ficha (especificación, bloque 2). As tempadas son un selector co
 * nome de cada unha (`[furarSegmented]`; a vixente hoxe leva «Agora»); debaixo, as datas da
 * escollida coa icona do calendario e a súa semana. No modo edición da ficha, as tempadas van nas
 * mesmas tarxetas ca no asistente (`furar-facility-season-list`), con «Engadir
 * tempada» debaixo. Nada vai á API ata que a barra garda (`SeasonEdits`): primeiro as que se
 * borran, despois as cambiadas e ao final as novas (con reservas afectadas, o modal de reservas afectadas).
 * Debaixo, as excepcións e, fóra da edición, o aviso de días sen tempada.
 */
@Component({
  selector: 'furar-facility-schedule',
  imports: [
    ButtonDirective,
    ExceptionListComponent,
    IconComponent,
    ScheduleGapsComponent,
    SeasonListComponent,
    SegmentedDirective,
    StagedFailuresComponent,
    StatusStateComponent,
    TagComponent,
    TranslocoPipe,
    WeekRowsComponent,
  ],
  providers: [ScheduleStore, ExceptionsStore],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './schedule-section.component.html',
})
export class ScheduleSectionComponent {
  protected readonly store = inject(ScheduleStore);
  protected readonly session = inject(EditSession);
  private readonly detail = inject(FacilityDetailStore);
  private readonly municipality = inject(CurrentMunicipality);
  private readonly affected = inject(AffectedBookingsFlow);

  private readonly transloco = inject(TranslocoService);
  private readonly translations = toSignal(this.transloco.langChanges$);
  private readonly lang = inject(LanguageService).lang;
  private readonly t = (key: string, params?: Record<string, unknown>): string => this.transloco.translate(key, params);

  protected readonly drafts = new SeasonEdits();
  protected readonly editing = this.session.editing;
  protected readonly chosen = signal<string | null>(null);
  private readonly problems = signal<readonly StagedFailure[]>([]);
  protected readonly failures = computed(() => (this.editing() ? this.problems() : []));
  private readonly facilityId = computed(() => this.detail.facility()?.id ?? null);

  protected readonly seasons = computed<readonly SeasonView[]>(() => {
    this.translations();
    const range = (dates: Pick<Season, 'startDate' | 'endDate'>): string => seasonRange(dates, this.lang(), this.t);
    return this.store.seasons().map((season) => ({
      key: season.id,
      name: season.name,
      range: range(season),
      week: season.week,
    }));
  });

  /** A vixente hoxe (leva «Agora»): a que di a API, se segue na lista. */
  protected readonly currentKey = computed(() => {
    const current = this.store.currentSeasonId();
    return this.seasons().find((s) => s.key === current)?.key ?? null;
  });

  /** A escollida; se non hai (ou xa non está), a vixente e, se non, a primeira. */
  protected readonly selectedKey = computed(() => {
    const seasons = this.seasons();
    const chosen = this.chosen();
    if (chosen && seasons.some((s) => s.key === chosen)) return chosen;
    return this.currentKey() ?? seasons[0]?.key ?? null;
  });
  protected readonly selected = computed(() => this.seasons().find((s) => s.key === this.selectedKey()) ?? null);

  constructor() {
    effect(() => {
      if (this.facilityId()) untracked(() => void this.load());
    });
    // Ao entrar en edición, as tempadas pasan ao borrador; ao saír (gardado ou cancelado), fóra.
    effect(() => {
      const editing = this.editing();
      untracked(() => {
        if (editing) this.drafts.start(this.store.seasons());
        else this.drafts.clear();
        this.problems.set([]);
      });
    });
    const leave = this.session.register({
      dirty: this.drafts.dirty,
      validate: () => this.drafts.validate(),
      save: () => this.save(),
      reset: () => this.drafts.clear(),
    });
    inject(DestroyRef).onDestroy(leave);
  }

  protected load(): Promise<void> {
    const slug = this.municipality.slug();
    const id = this.facilityId();
    return slug && id ? this.store.load(slug, id) : Promise.resolve();
  }

  /** Garda as tempadas: borradas, cambiadas e novas, unha detrás doutra. */
  private async save(): Promise<void> {
    const failures: StagedFailure[] = [];
    const fail = (name: string, error: unknown): void => {
      failures.push({ name, problem: asProblem(error) });
    };
    for (const id of this.drafts.removed()) {
      const name = this.store.get(id)?.name ?? '';
      try {
        const outcome = await this.affected.run((decisions) => this.store.remove(id, decisions));
        if (outcome.done) this.drafts.forget(id);
        else failures.push({ name, problem: cancelled() });
      } catch (error) {
        fail(name, error);
      }
    }
    // As cambiadas antes ca as novas: poden deixarlles sitio.
    const list = [...this.drafts.list()].sort((a, b) => Number(!a.id) - Number(!b.id));
    for (const season of list) {
      if (season.id && !season.dirty) continue;
      const body = season.body();
      try {
        if (!season.id) {
          const created = await this.store.create({
            name: body.name,
            startDate: body.startDate,
            ...(body.endDate ? { endDate: body.endDate } : {}),
          });
          season.id = created.id;
        }
        const seasonId = season.id;
        const outcome = await this.affected.run((decisions) => this.store.update(seasonId, body, decisions));
        if (outcome.done) season.markPristine();
        else failures.push({ name: body.name, problem: cancelled() });
      } catch (error) {
        // O choque vai no «Desde» e os erros dun tramo, na súa fila; todo, tamén ao aviso.
        season.showProblem(asProblem(error));
        fail(body.name, error);
      }
    }
    this.problems.set(failures);
    this.store.reload().catch(() => undefined);
    this.detail.reload().catch(() => undefined);
    if (failures.length) throw new Error('schedule');
  }
}

/** O xestor volveu atrás no modal de reservas afectadas: segue pendente. */
function cancelled(): ApiProblem {
  return new ApiProblem(409, 'facility.affected-bookings', {}, [], undefined, '');
}
