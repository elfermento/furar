// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { CardComponent } from '../shared/ui/card/card.component';
import type { ComboboxOption, ComboboxSource } from '../shared/ui/combobox/combobox.component';
import { ComboboxComponent } from '../shared/ui/combobox/combobox.component';
import { FieldComponent } from '../shared/ui/field.component';
import { MultiComboboxComponent } from '../shared/ui/multi-combobox/multi-combobox.component';
import { StatusStateComponent } from '../shared/ui/status-state/status-state.component';

/** Enderezos de mostra (nomes propios, sen traducir), coma os do buscador `fixed` da API. */
const ADDRESSES: readonly ComboboxOption<string>[] = [
  { value: 'naron-alto-12', label: 'Rúa do Alto, 12', detail: '15570 Narón' },
  { value: 'naron-galicia-1', label: 'Avenida de Galicia, 1', detail: '15570 Narón' },
  { value: 'naron-castelo-3', label: 'Estrada de Castela, 3', detail: '15570 Narón' },
  { value: 'ferrol-real-40', label: 'Rúa Real, 40', detail: '15402 Ferrol' },
  { value: 'ferrol-alameda-2', label: 'Alameda de Suanzes, 2', detail: '15402 Ferrol' },
];

/** Un deporte escollido: do catálogo polo código, ou creado polo texto. */
interface Sport {
  readonly code?: string;
  readonly label?: string;
}

const SPORTS = ['football', 'futsal', 'basketball', 'handball', 'padel', 'swimming', 'tennis', 'volleyball'] as const;

/** Busca sen distinguir maiúsculas nin acentos. */
const fold = (text: string) =>
  text
    .normalize('NFD')
    .replace(/\p{Diacritic}/gu, '')
    .toLocaleLowerCase();

const later = <T>(ms: number, value: () => T) =>
  new Promise<T>((resolve, reject) =>
    setTimeout(() => {
      try {
        resolve(value());
      } catch (error) {
        reject(error instanceof Error ? error : new Error(String(error)));
      }
    }, ms),
  );

/**
 * Sección «Campo con suxestións» do catálogo (só desenvolvemento): un enderezo cunha fonte lenta
 * (≥ 3 letras, 700 ms; «erro» simula o 503 do buscador) e deportes con «Crear» no campo de varios
 * valores con busca (`furar-multi-combobox`), máis o modo
 * `skeleton` de furar-status-state en filas e en tarxetas.
 */
@Component({
  selector: 'furar-catalog-search',
  imports: [
    TranslocoPipe,
    ReactiveFormsModule,
    CardComponent,
    ComboboxComponent,
    FieldComponent,
    MultiComboboxComponent,
    StatusStateComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <furar-card [heading]="'catalog.search.title' | transloco" id="suxestions">
      <div class="gap-form grid sm:grid-cols-2">
        <furar-field
          fieldId="catalog-address"
          [label]="'catalog.search.address' | transloco"
          [help]="'catalog.search.addressHelp' | transloco"
        >
          <furar-combobox
            inputId="catalog-address"
            describedBy="catalog-address-help"
            minLength="3"
            [formControl]="address"
            [label]="'catalog.search.address' | transloco"
            [source]="searchAddress"
            [displayWith]="addressLabel"
            (failed)="failed.set(true)"
            (selected)="failed.set(false)"
          />
          @if (failed()) {
            <p class="f-help">{{ 'catalog.search.manual' | transloco }}</p>
          }
        </furar-field>
        <furar-multi-combobox
          fieldId="catalog-sport"
          allowCreate
          [label]="'catalog.search.sport' | transloco"
          [tip]="'catalog.search.sportHelp' | transloco"
          [chosenLabel]="'catalog.search.chosen' | transloco"
          [createLabel]="'catalog.search.createSport' | transloco"
          [source]="searchSport"
          [key]="sportKey"
          [display]="sportLabel()"
          [outline]="custom"
          [(selected)]="sports"
          (create)="sportField.add({ label: $event })"
          #sportField
        />
      </div>
      <div class="gap-form mt-6 grid">
        <furar-status-state mode="skeleton" [rows]="3" />
        <furar-status-state mode="skeleton" shape="card" [rows]="3" />
      </div>
    </furar-card>
  `,
})
export class CatalogSearchComponent {
  private readonly transloco = inject(TranslocoService);
  protected readonly address = new FormControl<string | null>(null);
  protected readonly failed = signal(false);
  private readonly lang = toSignal(this.transloco.langChanges$);
  /** Escollidos: os do catálogo polo código (Pádel, de mostra, para ver o chip ao abrir) e os creados polo texto, con bordo. */
  protected readonly sports = signal<readonly Sport[]>([{ code: 'padel' }]);

  protected readonly addressLabel = (value: string) => ADDRESSES.find((a) => a.value === value)?.label ?? value;

  protected readonly searchAddress: ComboboxSource<string> = (query) =>
    later(700, () => {
      if (fold(query).includes('erro')) {
        throw new Error('geocoder-unavailable');
      }
      return ADDRESSES.filter((a) => fold(`${a.label} ${a.detail ?? ''}`).includes(fold(query)));
    });

  protected readonly searchSport: ComboboxSource<Sport> = (query) =>
    later(300, () =>
      SPORTS.map((code) => ({ value: { code }, label: this.sportLabel()({ code }) })).filter((s) =>
        fold(s.label).includes(fold(query)),
      ),
    );

  /** Nome de cada deporte no idioma activo: os do catálogo, traducidos; os creados, o texto. */
  protected readonly sportLabel = computed(() => {
    this.lang();
    return ({ code, label }: Sport) =>
      code ? this.transloco.translate('catalog.search.sports.' + code) : (label ?? '');
  });
  protected readonly sportKey = ({ code, label }: Sport) => code ?? `label:${label}`;
  protected readonly custom = (sport: Sport) => !sport.code;
}
