// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { ElementRef } from '@angular/core';
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
import { TranslocoPipe } from '@jsverse/transloco';
import { firstValueFrom, merge, of, switchMap } from 'rxjs';
import { CurrentMunicipality } from '../../../core/municipality/current-municipality';
import { DeleteButtonComponent } from '../../../shared/ui/delete-button/delete-button.component';
import { localized } from '../../../shared/custom-fields/localized';
import type { ComboboxOption, ComboboxSource } from '../../../shared/ui/combobox/combobox.component';
import { ComboboxComponent } from '../../../shared/ui/combobox/combobox.component';
import { DialogService } from '../../../shared/ui/dialog/dialog.service';
import { describedBy, fieldError } from '../../../shared/ui/field-errors';
import { FieldComponent } from '../../../shared/ui/field.component';
import type { CreateSportDialogData } from './create-sport-dialog.component';
import { CreateSportDialogComponent } from './create-sport-dialog.component';
import { DataCardStore } from './data-card.store';
import type { SportResponse } from './data-form';
import { SPORTS_MAX, sameSport } from './data-form';

/** Suxestións que se ensinan como moito: o resto, escribindo máis. */
const SUGGESTIONS = 10;
const FIELD_ID = 'facility-data-sport';

/** Para buscar sen distinguir maiúsculas nin acentos («futbol» atopa «Fútbol»). */
export const fold = (text: string): string => text.normalize('NFD').replace(/\p{M}/gu, '').toLocaleLowerCase();

/**
 * Deportes da tarxeta Datos (D1-21d; especificación, bloque 1: «catálogo de Furar con + crear»):
 * os escollidos nunha lista, cada un co seu «Quitar», e un `furar-combobox` para engadir máis.
 * Busca no catálogo e nos do concello (pídense unha vez, ao buscar por primeira vez), sen os xa
 * escollidos. «Crear deporte» abre o diálogo co nome en gl e es (P26); o novo engádese á lista.
 * Cada cambio marca a sección como sucia: só «Gardar» o leva á instalación.
 */
@Component({
  selector: 'furar-facility-sports-field',
  imports: [ComboboxComponent, DeleteButtonComponent, FieldComponent, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @let t = 'facility.data.sports.';
    <div class="gap-unit grid" #root>
      @if (chosen().length) {
        <ul class="grid" [attr.aria-label]="t + 'chosen' | transloco">
          @for (sport of chosen(); track sportKey(sport); let i = $index) {
            <li class="gap-unit flex items-center justify-between">
              <span class="min-w-0 break-words">{{ name(sport) }}</span>
              <furar-delete-button
                [attr.data-remove]="i"
                [label]="t + 'remove' | transloco: { name: name(sport) }"
                (pressed)="remove(i)"
              />
            </li>
          }
        </ul>
      }
      <furar-field
        [fieldId]="fieldId"
        [required]="required()"
        [label]="(required() ? t + 'label' : t + 'add') | transloco"
        [help]="t + 'help' | transloco: { max: max }"
        [error]="error()"
      >
        <furar-combobox
          #combobox
          clearOnSelect
          allowCreate
          [inputId]="fieldId"
          [label]="t + 'add' | transloco"
          [createLabel]="t + 'create' | transloco"
          [source]="source"
          [invalid]="!!error()"
          [describedBy]="describedBy(fieldId, true, error())"
          (selected)="add($event.value)"
          (create)="create($event)"
        />
      </furar-field>
    </div>
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
  private readonly root = viewChild.required<ElementRef<HTMLElement>>('root');
  private readonly combobox = viewChild.required<ComboboxComponent<SportResponse>>('combobox');
  /** Catálogo + deportes do concello; pídese ao buscar por primeira vez. */
  private catalog: Promise<SportResponse[]> | null = null;

  protected readonly fieldId = FIELD_ID;
  protected readonly max = SPORTS_MAX;
  protected readonly describedBy = describedBy;
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

  protected readonly source: ComboboxSource<SportResponse> = async (query) => {
    const all = await this.loadCatalog();
    const wanted = fold(query);
    const chosen = this.control().value;
    return all
      .filter((sport) => !chosen.some((other) => sameSport(other, sport)))
      .map((sport): ComboboxOption<SportResponse> => ({ value: sport, label: this.name(sport) }))
      .filter((option) => fold(option.label).includes(wanted))
      .sort((a, b) => a.label.localeCompare(b.label, this.lang()))
      .slice(0, SUGGESTIONS);
  };

  protected name(sport: SportResponse): string {
    return localized(sport.label, this.lang());
  }

  protected sportKey(sport: SportResponse): string {
    return sport.code ?? `custom:${sport.customSportId}`;
  }

  protected add(sport: SportResponse): void {
    const current = this.control().value;
    if (current.some((other) => sameSport(other, sport))) return;
    this.set([...current, sport]);
  }

  /** Quita o deporte e leva o foco ao seguinte «Quitar» (ou ao buscador, se non queda ningún). */
  protected remove(index: number): void {
    const next = this.control().value.filter((_, i) => i !== index);
    this.set(next);
    afterNextRender(
      () => {
        const buttons = this.root().nativeElement.querySelectorAll<HTMLButtonElement>('[data-remove] button');
        const target = buttons[Math.min(index, buttons.length - 1)];
        if (target) target.focus();
        else this.combobox().focus();
      },
      { injector: this.injector },
    );
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
    if (sport) this.add(sport);
    afterNextRender(() => this.combobox().focus(), { injector: this.injector });
  }

  private set(sports: SportResponse[]): void {
    const control = this.control();
    control.setValue(sports);
    control.markAsDirty();
    control.markAsTouched();
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
