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
  model,
  untracked,
} from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { ScheduleFacilitiesStore } from '../../../shared/schedule/schedule-facilities.store';
import { ScheduleFieldsComponent } from '../../../shared/schedule/schedule-fields.component';
import { FieldComponent } from '../../../shared/ui/field.component';
import { InputDirective } from '../../../shared/ui/input/input.directive';
import type { SelectOption } from '../../../shared/ui/select/select-option';
import { SelectComponent } from '../../../shared/ui/select/select.component';
import type { DraftErrors, SeasonDraft } from './seasons';
import { NOTE_MAX } from './seasons';

let nextId = 0;

/**
 * Os campos dunha tempada: na xestión (Nova reserva › Tempada e revisar unha
 * solicitude) e na entidade (Solicitar tempada). Unha cápsula con Entidade (se se pasan
 * `organizations`), Instalación, Que (só usos enteiros ou por partes: os por prazas non valen para
 * unha entidade), Parte (nos usos por partes), Días (o panel cos días da semana), Horas (o panel con
 * Desde e Ata, de 15 en 15) e Datas (o calendario de rango con «Aplicar»); con `note`, a Nota debaixo. As
 * instalacións son as que se poden reservar (a lista pública). O valor vive en `draft` (dobre
 * vínculo) e os erros pásaos quen o usa (`errors`), que decide cando se ven.
 */
@Component({
  selector: 'furar-season-form',
  imports: [FieldComponent, InputDirective, ScheduleFieldsComponent, SelectComponent, TranslocoPipe],
  providers: [ScheduleFacilitiesStore],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'f-season-form' },
  templateUrl: './season-form.component.html',
})
export class SeasonFormComponent {
  private readonly store = inject(ScheduleFacilitiesStore);

  /** O concello. */
  readonly slug = input.required<string>();
  /** O que vai escollido (dobre vínculo). */
  readonly draft = model.required<SeasonDraft>();
  /** As entidades para escoller (na xestión); sen elas, sen campo Entidade (vai na ruta). */
  readonly organizations = input<readonly SelectOption<string>[] | null>(null);
  /** A Nota (a solicitude da entidade). */
  readonly note = input(false, { transform: booleanAttribute });
  /** Os erros que se ven (quen o usa decide cando). */
  readonly errors = input<DraftErrors>({});

  protected readonly id = `furar-season-${nextId++}`;
  protected readonly noteMax = NOTE_MAX;

  private readonly facilities = this.store.facilities;
  private readonly facility = this.store.facility;

  protected readonly facilityOptions = computed<SelectOption<string>[]>(() =>
    this.facilities().map((f) => ({ value: f.id, label: f.name })),
  );
  /** Os usos que valen para unha entidade: enteiros ou por partes. */
  private readonly uses = computed(() => (this.facility()?.uses ?? []).filter((use) => use.rentalMode !== 'SEATS'));
  protected readonly useOptions = computed<SelectOption<string>[]>(() =>
    this.uses().map((use) => ({ value: use.id, label: use.name })),
  );
  private readonly use = computed(() => this.uses().find((use) => use.id === this.draft().useId) ?? null);
  /** O uso escollido vai por partes: hai que dicir cal. */
  readonly partRequired = computed(() => this.use()?.rentalMode === 'PARTS');
  protected readonly partOptions = computed<SelectOption<string>[]>(() =>
    (this.use()?.parts ?? []).map((part) => ({ value: part.id, label: part.name })),
  );

  constructor() {
    effect(() => {
      const slug = this.slug();
      untracked(() => void this.store.loadAll(slug));
    });
    // A instalación escollida (tamén a que trae unha solicitude): os seus usos e partes.
    effect(() => {
      const facilityId = this.draft().facilityId;
      const slug = this.slug();
      untracked(() => void this.loadFacility(slug, facilityId));
    });
  }

  protected patch(change: Partial<SeasonDraft>): void {
    this.draft.update((draft) => ({ ...draft, ...change }));
  }

  protected pickFacility(facilityId: string): void {
    if (facilityId === this.draft().facilityId) return;
    this.patch({ facilityId, useId: null, partId: null });
  }

  protected pickUse(useId: string): void {
    if (useId === this.draft().useId) return;
    this.patch({ useId, partId: null });
  }

  protected writeNote(event: Event): void {
    this.patch({ note: (event.target as HTMLTextAreaElement).value });
  }

  /** A instalación escollida; cun só uso que vale, xa vai escollido. */
  private async loadFacility(slug: string, facilityId: string | null): Promise<void> {
    const facility = await this.store.load(slug, facilityId);
    if (!facility || this.draft().facilityId !== facilityId) return;
    const uses = facility.uses.filter((use) => use.rentalMode !== 'SEATS');
    if (!this.draft().useId && uses.length === 1) this.patch({ useId: uses[0].id });
  }
}
