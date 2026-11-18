// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  Injector,
  afterNextRender,
  booleanAttribute,
  computed,
  effect,
  inject,
  input,
  output,
  signal,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ReactiveFormsModule } from '@angular/forms';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { merge } from 'rxjs';
import { LanguageService } from '../../../core/i18n/language.service';
import type { ControlError } from '../../../shared/custom-fields/custom-field-controls';
import { ButtonDirective } from '../../../shared/ui/button/button.directive';
import { DateFieldComponent } from '../../../shared/ui/date-field/date-field.component';
import { DeleteButtonComponent } from '../../../shared/ui/delete-button/delete-button.component';
import { fieldError } from '../../../shared/ui/field-errors';
import { FieldComponent } from '../../../shared/ui/field.component';
import { FieldGroupComponent } from '../../../shared/ui/field-group/field-group.component';
import { FieldLineComponent } from '../../../shared/ui/field-line/field-line.component';
import { IconComponent } from '../../../shared/ui/icon/icon.component';
import { InputDirective } from '../../../shared/ui/input/input.directive';
import {
  SegmentedOptionDirective,
  SegmentedRadioDirective,
} from '../../../shared/ui/segmented/segmented-radio.directive';
import { SegmentedDirective } from '../../../shared/ui/segmented/segmented.directive';
import type { SelectOption } from '../../../shared/ui/select/select.component';
import { SelectComponent } from '../../../shared/ui/select/select.component';
import { SwitchComponent } from '../../../shared/ui/switch/switch.component';
import { TimePickerComponent } from '../../../shared/ui/time-picker/time-picker.component';
import { DayRowComponent } from './day-row.component';
import { DAYS, HOLIDAYS_ROW } from './schedule.store';
import type { SeasonEditor } from './season-editor';
import { overlapOf } from './season-editor';
import { weekdayName } from './week-format';

/** Unha tempada tal como se pinta: o título (o nome ou «Nova tempada») e os seus erros. */
interface SeasonView {
  readonly season: SeasonEditor;
  readonly id: string;
  readonly title: string;
  readonly hasEnd: boolean;
  readonly name: ControlError | null;
  readonly start: ControlError | null;
  readonly end: ControlError | null;
  readonly same: ControlError | null;
  readonly noOpenDay: boolean;
  /** «Empezar co horario de»: só nas novas, coas demais tempadas. */
  readonly copy: readonly SelectOption<string>[];
}

/** «Empezar co horario de»: a tempada nova e a escollida. */
export interface SeasonCopy {
  readonly season: SeasonEditor;
  readonly from: SeasonEditor;
}

/**
 * As tempadas en edición, as mesmas no paso «Horario» do asistente e no modo
 * edición da ficha: cada unha na súa tarxeta (cabeceira tinguida co nome ou «Nova tempada» e a
 * papeleira; nome, o interruptor «Con data de fin», «Desde» e, só co interruptor aceso, «Ata»; e as
 * horas) e,
 * debaixo, «Engadir tempada». Por defecto, o mesmo horario todos os días; «Por días» abre a semana
 * coas filas da ficha. Se as datas dunha pisan as dunha anterior, o erro vai no seu «Desde».
 * Quen a usa ten a lista e fai o que piden `addSeason`, `removeSeason` e `copyHours`; o foco
 * móveo ela.
 */
@Component({
  selector: 'furar-facility-season-list',
  imports: [
    ButtonDirective,
    DateFieldComponent,
    DayRowComponent,
    DeleteButtonComponent,
    FieldComponent,
    FieldGroupComponent,
    FieldLineComponent,
    IconComponent,
    InputDirective,
    ReactiveFormsModule,
    SegmentedDirective,
    SegmentedOptionDirective,
    SegmentedRadioDirective,
    SelectComponent,
    SwitchComponent,
    TimePickerComponent,
    TranslocoPipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'f-season-list' },
  templateUrl: './season-list.component.html',
})
export class SeasonListComponent {
  readonly seasons = input.required<readonly SeasonEditor[]>();
  /** Prefixo dos ids (`wizard-season`, `facility-season`). */
  readonly idPrefix = input.required<string>();
  /** Xa se intentou seguir ou gardar: os erros, á vista aínda que non se tocase o campo. */
  readonly tried = input(false, { transform: booleanAttribute });
  /** Pide polo menos un día aberto (no asistente); na ficha, unha tempada pode ir pechada. */
  readonly requireOpenDay = input(true, { transform: booleanAttribute });
  /** Pódese quitar tamén a última (na ficha); no asistente, sempre queda unha. */
  readonly allowEmpty = input(false, { transform: booleanAttribute });
  readonly addSeason = output();
  readonly removeSeason = output<SeasonEditor>();
  readonly copyHours = output<SeasonCopy>();

  private readonly language = inject(LanguageService);
  private readonly transloco = inject(TranslocoService);
  private readonly translations = toSignal(this.transloco.selectTranslation());
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly injector = inject(Injector);
  /** Cada cambio nos formularios das tempadas (non son signals). */
  private readonly tick = signal(0);

  constructor() {
    effect((onCleanup) => {
      const subscription = merge(...this.seasons().map((season) => season.all.events)).subscribe(() =>
        this.tick.update((n) => n + 1),
      );
      onCleanup(() => subscription.unsubscribe());
    });
  }

  /** «Mesmo horario» coa hora de peche antes da de apertura: o erro vai debaixo de «Ata». */
  protected readonly endBeforeStart: ControlError = {
    key: 'facility.schedule.errors.endBeforeStart',
    params: { n: 1 },
  };
  protected readonly views = computed<readonly SeasonView[]>(() => {
    this.tick();
    this.translations();
    const tried = this.tried();
    this.requireOpenDay();
    const list = this.seasons();
    const prefix = this.idPrefix();
    const title = (season: SeasonEditor): string =>
      season.name || this.transloco.translate('facility.wizard.schedule.newSeason');
    return list.map((season) => {
      const c = season.form.controls;
      const clash = overlapOf(list, season);
      const sameShown = (season.sameHours.touched || tried) && season.sameHours.invalid;
      return {
        season,
        id: `${prefix}-${season.key}`,
        title: title(season),
        hasEnd: season.hasEnd.value,
        name: fieldError(c.name, tried),
        start: clash
          ? { key: 'errors.facility.season-overlap', params: { name: title(clash) } }
          : fieldError(c.startDate, tried),
        end: fieldError(c.endDate, tried),
        same: sameShown ? fieldError(season.sameHours, true) : null,
        noOpenDay: tried && this.requireOpenDay() && season.noOpenDay(),
        copy:
          season.added && !season.id
            ? list.filter((other) => other !== season).map((other) => ({ value: other.key, label: title(other) }))
            : [],
      };
    });
  });

  protected readonly removable = computed(() => this.allowEmpty() || this.seasons().length > 1);

  protected readonly names = computed(() => {
    this.translations();
    const lang = this.language.lang();
    return DAYS.map((_, i) =>
      i === HOLIDAYS_ROW ? this.transloco.translate('facility.schedule.day.holidays') : weekdayName(i, lang),
    );
  });

  /** «Engadir tempada»: a nova vai ao final e o foco, ao seu nome. */
  protected added(): void {
    this.addSeason.emit();
    afterNextRender(
      () => {
        const names = this.host.nativeElement.querySelectorAll<HTMLElement>('[data-season-name]');
        names[names.length - 1]?.focus();
      },
      { injector: this.injector },
    );
  }

  protected removed(season: SeasonEditor): void {
    this.removeSeason.emit(season);
    this.focus('[data-season-add]');
  }

  protected copied(season: SeasonEditor, key: string): void {
    const from = this.seasons().find((other) => other.key === key);
    if (from) this.copyHours.emit({ season, from });
  }

  private focus(selector: string): void {
    afterNextRender(() => this.host.nativeElement.querySelector<HTMLElement>(selector)?.focus(), {
      injector: this.injector,
    });
  }
}
