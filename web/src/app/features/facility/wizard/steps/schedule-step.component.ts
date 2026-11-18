// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import type { SeasonEditor } from '../../schedule/season-editor';
import type { SeasonCopy } from '../../schedule/season-list.component';
import { SeasonListComponent } from '../../schedule/season-list.component';
import { STEP } from '../wizard';
import { WizardStore } from '../wizard.store';

/**
 * Paso 6, «Horario»: as tempadas en tarxetas (`furar-facility-season-list`, as
 * mesmas ca no modo edición da ficha) e, debaixo, «Engadir tempada». A primeira empeza sen nome,
 * desde o 1 de xaneiro e sen fin; as novas, co horario da anterior.
 */
@Component({
  selector: 'furar-wizard-schedule-step',
  imports: [SeasonListComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <furar-facility-season-list
      idPrefix="wizard-season"
      [seasons]="store.seasons()"
      [tried]="tried()"
      (addSeason)="store.forms.addSeason()"
      (removeSeason)="remove($event)"
      (copyHours)="copy($event)"
    />
  `,
})
export class ScheduleStepComponent {
  protected readonly store = inject(WizardStore);
  protected readonly tried = computed(() => this.store.tried().has(STEP.schedule));

  protected remove(season: SeasonEditor): void {
    this.store.forms.removeSeason(season);
  }

  protected copy({ season, from }: SeasonCopy): void {
    this.store.forms.copySeasonHours(season, from);
  }
}
