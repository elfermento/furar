// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  Injector,
  afterNextRender,
  computed,
  effect,
  inject,
  signal,
  untracked,
} from '@angular/core';
import { ReactiveFormsModule } from '@angular/forms';
import { toSignal } from '@angular/core/rxjs-interop';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { LanguageService } from '../../../core/i18n/language.service';
import { firstValueFrom } from 'rxjs';
import { ApiProblem } from '../../../core/errors/api-problem';
import { CurrentMunicipality } from '../../../core/municipality/current-municipality';
import { ButtonDirective } from '../../../shared/ui/button/button.directive';
import { DateFieldComponent } from '../../../shared/ui/date-field/date-field.component';
import { DeleteButtonComponent } from '../../../shared/ui/delete-button/delete-button.component';
import { DialogService } from '../../../shared/ui/dialog/dialog.service';
import { EditSession } from '../../../shared/ui/edit-session/edit-session';
import { asProblem } from '../../../shared/ui/edit-section/section-errors';
import type { ControlError } from '../../../shared/custom-fields/custom-field-controls';
import { fieldError } from '../../../shared/ui/field-errors';
import { FieldComponent } from '../../../shared/ui/field.component';
import { IconComponent } from '../../../shared/ui/icon/icon.component';
import { InputDirective } from '../../../shared/ui/input/input.directive';
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
import type { SeasonDialogData, SeasonDialogValue } from './season-dialog.component';
import { SeasonDialogComponent } from './season-dialog.component';
import type { SeasonDraft } from './season-drafts';
import { SeasonDrafts, clashing, draftBody, draftDates, draftWeek } from './season-drafts';
import type { WeekForm } from './week-form';
import { applyWeekErrors } from './week-form';
import { seasonRange } from './week-format';
import { WeekRowsComponent } from './week-rows.component';
import { RequiredNoteComponent } from '../../../shared/ui/required/required.component';

/** Unha tempada tal como se pinta: gardada ou do borrador do modo edición. */
interface SeasonView {
  readonly key: string;
  readonly name: string;
  readonly range: string;
  readonly week: readonly DayHours[];
}

/**
 * Sección «Horario» da ficha (D1-23a; especificación, bloque 2). As tempadas son un selector co
 * nome de cada unha (`[furarSegmented]`; a vixente hoxe leva «Agora»); debaixo, as datas da
 * escollida coa icona do calendario e a súa semana. No modo edición da ficha, o selector leva «+» para engadir unha tempada (modal), a
 * escollida mostra nome e datas como campos e «Eliminar» na mesma liña, e as filas da semana
 * ábrense ao pulsalas. Nada vai á API ata que a barra garda (`SeasonDrafts`): primeiro as que se
 * borran, despois as cambiadas e ao final as novas (con reservas afectadas, o modal de D1-19).
 * Debaixo, as excepcións e, fóra da edición, o aviso de días sen tempada (D1-23b).
 */
@Component({
  selector: 'furar-facility-schedule',
  imports: [
    RequiredNoteComponent,
    ButtonDirective,
    DateFieldComponent,
    DeleteButtonComponent,
    ExceptionListComponent,
    FieldComponent,
    IconComponent,
    InputDirective,
    ReactiveFormsModule,
    ScheduleGapsComponent,
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
  private readonly dialogs = inject(DialogService);
  private readonly affected = inject(AffectedBookingsFlow);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly injector = inject(Injector);

  private readonly transloco = inject(TranslocoService);
  private readonly translations = toSignal(this.transloco.langChanges$);
  private readonly lang = inject(LanguageService).lang;
  private readonly t = (key: string, params?: Record<string, unknown>): string => this.transloco.translate(key, params);

  protected readonly drafts = new SeasonDrafts();
  protected readonly editing = this.session.editing;
  protected readonly chosen = signal<string | null>(null);
  private readonly problems = signal<readonly StagedFailure[]>([]);
  protected readonly failures = computed(() => (this.editing() ? this.problems() : []));
  private readonly facilityId = computed(() => this.detail.facility()?.id ?? null);

  protected readonly seasons = computed<readonly SeasonView[]>(() => {
    this.translations();
    const range = (dates: Pick<Season, 'startDate' | 'endDate'>): string => seasonRange(dates, this.lang(), this.t);
    return this.editing()
      ? this.drafts.list().map((draft) => ({
          key: draft.key,
          name: draft.form.controls.name.value.trim() || draft.saved?.name || '',
          range: range(draftDates(draft.form)),
          week: draftWeek(draft.form),
        }))
      : this.store.seasons().map((season) => ({
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

  protected errorOf(draft: SeasonDraft, name: 'name' | 'startDate' | 'endDate'): ControlError | null {
    this.drafts.list();
    return fieldError(draft.form.controls[name], false);
  }

  /** «+»: nome, datas e «empezar co horario de»; queda pendente ata gardar. */
  protected async add(): Promise<void> {
    const others = this.drafts.list();
    const data: SeasonDialogData = {
      seasons: others.map((draft) => ({
        id: draft.key,
        name: draft.form.controls.name.value,
        startDate: draft.form.controls.startDate.value ?? '',
        endDate: draft.form.controls.endDate.value,
        version: 0,
        week: draftWeek(draft.form),
      })),
      copyFrom: this.selectedKey(),
      save: async (value: SeasonDialogValue) => {
        const clash = clashing(others, value);
        if (clash) {
          throw new ApiProblem(
            409,
            'facility.season-overlap',
            { name: clash.form.controls.name.value },
            [],
            undefined,
            '',
          );
        }
        const from = value.copyFrom ? this.drafts.get(value.copyFrom) : undefined;
        const key = this.drafts.add(value, from ? draftWeek(from.form) : []);
        return { id: key, name: value.name, startDate: value.startDate, endDate: value.endDate, version: 0, week: [] };
      },
    };
    const ref = this.dialogs.open<Season, SeasonDialogData>(SeasonDialogComponent, { data });
    const added = await firstValueFrom(ref.closed, { defaultValue: undefined });
    if (!added) return;
    this.chosen.set(added.id);
    this.focus(`[data-season-id="${added.id}"]`);
  }

  protected remove(key: string): void {
    this.drafts.remove(key);
    this.chosen.set(null);
    this.focus('[data-seasons] button');
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
    for (const draft of this.drafts.list()) {
      if (draft.saved && !draft.form.dirty) continue;
      const body = draftBody(draft.form);
      try {
        let id = draft.saved?.id ?? null;
        if (!id) {
          const created = await this.store.create({
            name: body.name,
            startDate: body.startDate,
            ...(body.endDate ? { endDate: body.endDate } : {}),
          });
          this.drafts.replace(draft.key, created, draft.form);
          id = created.id;
        }
        const seasonId = id;
        const outcome = await this.affected.run((decisions) => this.store.update(seasonId, body, decisions));
        if (outcome.done) draft.form.markAsPristine();
        else failures.push({ name: body.name, problem: cancelled() });
      } catch (error) {
        // Os erros dun tramo (`week[2].intervals[1]`) van á súa fila; o resto, ao aviso.
        applyWeekErrors(draft.form as unknown as WeekForm, asProblem(error));
        fail(body.name, error);
      }
    }
    this.problems.set(failures);
    this.store.reload().catch(() => undefined);
    this.detail.reload().catch(() => undefined);
    if (failures.length) throw new Error('schedule');
  }

  private focus(selector: string): void {
    afterNextRender(() => this.host.nativeElement.querySelector<HTMLElement>(selector)?.focus(), {
      injector: this.injector,
    });
  }
}

/** O xestor volveu atrás no modal de reservas afectadas: segue pendente. */
function cancelled(): ApiProblem {
  return new ApiProblem(409, 'facility.affected-bookings', {}, [], undefined, '');
}
