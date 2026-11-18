// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { MunicipalityDirectory } from '../../../core/municipality/municipality-directory';
import { ButtonDirective } from '../button/button.directive';
import { IconComponent } from '../icon/icon.component';

/**
 * Botón redondo «←» (nome accesible «Todos os concellos») enriba do título da portada e de «Instalacións» dun concello: volve á
 * portada común (`/`, ADR-0030), sen levar os filtros. Só cando a instalación ten varios concellos,
 * a mesma regra ca a raíz (`severalMunicipalitiesMatch`): usa a lista que xa leu a raíz e, se aínda
 * non se leu (entrouse directo no concello), pídea unha vez; se falla, sen ligazón.
 */
@Component({
  selector: 'furar-all-municipalities-link',
  imports: [ButtonDirective, IconComponent, RouterLink, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'contents' },
  template: `
    @if (several()) {
      <a
        routerLink="/"
        furarButton
        round
        data-all-municipalities
        [attr.aria-label]="'ui.allMunicipalities' | transloco"
      >
        <furar-icon name="arrow-left" />
      </a>
    }
  `,
})
export class AllMunicipalitiesLinkComponent {
  private readonly directory = inject(MunicipalityDirectory);
  protected readonly several = computed(() => (this.directory.municipalities()?.length ?? 0) > 1);

  constructor() {
    if (!this.directory.municipalities()) this.directory.list().catch(() => undefined);
  }
}
