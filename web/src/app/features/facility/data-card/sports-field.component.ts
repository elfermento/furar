// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import {
  ChangeDetectionStrategy,
  Component,
  Injector,
  afterNextRender,
  booleanAttribute,
  computed,
  inject,
  input,
  viewChild,
} from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import type { FormControl } from '@angular/forms';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { firstValueFrom, merge, of, switchMap } from 'rxjs';
import { CurrentMunicipality } from '../../../core/municipality/current-municipality';
import { localized } from '../../../shared/custom-fields/localized';
import type { ComboboxOption, ComboboxSource } from '../../../shared/ui/combobox/combobox.component';
import { DialogService } from '../../../shared/ui/dialog/dialog.service';
import { fieldError } from '../../../shared/ui/field-errors';
import { MultiComboboxComponent } from '../../../shared/ui/multi-combobox/multi-combobox.component';
import type { CreateSportDialogData } from './create-sport-dialog.component';
import { CreateSportDialogComponent } from './create-sport-dialog.component';
import { DataCardStore } from './data-card.store';
import type { SportResponse } from './data-form';
import { SPORTS_MAX } from './data-form';

/** Suxestións que se ensinan como moito: o resto, escribindo máis. */
const SUGGESTIONS = 10;
const FIELD_ID = 'facility-data-sport';

/** Para buscar sen distinguir maiúsculas nin acentos («futbol» atopa «Fútbol»). */
export const fold = (text: string): string => text.normalize('NFD').replace(/\p{M}/gu, '').toLocaleLowerCase();

/**
 * Deportes da tarxeta Datos (especificación, bloque 1: «catálogo de Furar con + crear»): o campo
 * de varios valores con busca (`furar-multi-combobox`). Busca no catálogo e nos do concello
 * (pídense unha vez, ao buscar por primeira vez); os do concello levan un fino bordo. «Crear
 * deporte» abre o diálogo co nome en gl e es; o novo engádese á lista. Cada cambio marca a sección
 * como sucia: só «Gardar» o leva á instalación.
 */
@Component({
  selector: 'furar-facility-sports-field',
  imports: [MultiComboboxComponent, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @let t = 'facility.data.sports.';
    <furar-multi-combobox
      #field
      allowCreate
      [fieldId]="fieldId"
      [required]="required()"
      [label]="(required() ? t + 'label' : t + 'add') | transloco"
      [tip]="t + 'help' | transloco: { max: max }"
      [error]="error()"
      [chosenLabel]="t + 'chosen' | transloco"
      [createLabel]="t + 'create' | transloco"
      [limit]="suggestions"
      [source]="source"
      [key]="sportKey"
      [display]="name()"
      [outline]="custom"
      [countText]="countText"
      [selected]="chosen()"
      (selectedChange)="set($event)"
      (create)="create($event)"
    />
  `,
})
export class SportsFieldComponent {
  /** Control da lista de deportes do formulario da tarxeta. */
  readonly control = input.required<FormControl<SportResponse[]>>();
  readonly lang = input.required<string>();
  /** Asistente: o campo chámase «Deportes» e leva a marca de obrigatorio (fai falta un). */
  readonly required = input(false, { transform: booleanAttribute });

  private readonly store = inject(DataCardStore);
  private readonly municipality = inject(CurrentMunicipality);
  private readonly dialogs = inject(DialogService);
  private readonly injector = inject(Injector);
  private readonly transloco = inject(TranslocoService);
  private readonly field = viewChild.required<MultiComboboxComponent<SportResponse>>('field');
  /** Catálogo + deportes do concello; pídese ao buscar por primeira vez. */
  private catalog: Promise<SportResponse[]> | null = null;

  protected readonly fieldId = FIELD_ID;
  protected readonly max = SPORTS_MAX;
  protected readonly suggestions = SUGGESTIONS;
  /** Cambia co estado do control actual (valor, tocado, erros da API). */
  private readonly events = toSignal(
    toObservable(this.control).pipe(switchMap((control) => merge(of(null), control.events))),
  );
  protected readonly chosen = computed(() => {
    this.events();
    return this.control().value;
  });
  protected readonly error = computed(() => {
    this.events();
    return fieldError(this.control(), false);
  });
  /** O nome no idioma da tarxeta. */
  protected readonly name = computed(() => {
    const lang = this.lang();
    return (sport: SportResponse) => localized(sport.label, lang);
  });

  protected readonly source: ComboboxSource<SportResponse> = async (query) => {
    const all = await this.loadCatalog();
    const wanted = fold(query);
    const name = this.name();
    return all
      .map((sport): ComboboxOption<SportResponse> => ({ value: sport, label: name(sport) }))
      .filter((option) => fold(option.label).includes(wanted))
      .sort((a, b) => a.label.localeCompare(b.label, this.lang()));
  };

  protected readonly sportKey = (sport: SportResponse): string => sport.code ?? `custom:${sport.customSportId}`;
  protected readonly custom = (sport: SportResponse): boolean => !sport.code;
  protected readonly countText = (count: number): string =>
    this.transloco.translate('facility.data.sports.' + (count === 1 ? 'countOne' : 'countOther'), { count });

  protected set(sports: readonly SportResponse[]): void {
    const control = this.control();
    control.setValue([...sports]);
    control.markAsDirty();
    control.markAsTouched();
  }

  protected async create(name: string): Promise<void> {
    const slug = this.municipality.slug();
    if (!slug) return;
    const data: CreateSportDialogData = {
      name,
      create: async (label) => {
        const sport = await this.store.createSport(slug, label);
        this.catalog = this.catalog?.then((all) => [...all, sport]) ?? null;
        return sport;
      },
    };
    const ref = this.dialogs.open<SportResponse, CreateSportDialogData>(CreateSportDialogComponent, { data });
    const sport = await firstValueFrom(ref.closed, { defaultValue: undefined });
    if (sport) this.field().add(sport);
    afterNextRender(() => this.field().focus(), { injector: this.injector });
  }

  private loadCatalog(): Promise<SportResponse[]> {
    const slug = this.municipality.slug();
    if (!slug) return Promise.resolve([]);
    this.catalog ??= this.store.sports(slug).catch((error: unknown) => {
      this.catalog = null;
      throw error;
    });
    return this.catalog;
  }
}
