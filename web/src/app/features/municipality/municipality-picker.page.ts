// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { OnDestroy, OnInit } from '@angular/core';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import type { MunicipalitySummaryResponse } from './municipality.store';
import { PlatformAccessService } from '../../core/auth/platform-access.service';
import { NEW_MUNICIPALITY_URL } from '../../core/auth/roles';
import { SessionService } from '../../core/auth/session.service';
import type { ApiProblem } from '../../core/errors/api-problem';
import { asProblem } from '../../core/errors/problem.interceptor';
import { FirstRun } from '../../core/municipality/first-run';
import { PageHeaderComponent } from '../../shared/ui/page-header/page-header.component';
import { ProblemAlertComponent } from '../../shared/ui/problem-alert.component';
import { RowListDirective } from '../../shared/ui/row/row-list.directive';
import { RowComponent } from '../../shared/ui/row/row.component';
import { StatusStateComponent } from '../../shared/ui/status-state/status-state.component';
import { MunicipalityHomePage } from './municipality-home.page';
import { MunicipalityStore } from './municipality.store';

/** O que mostra a raíz segundo os concellos que hai. */
type RootView = 'loading' | 'showcase' | 'list';

/**
 * Raíz (`/`, ADR-0028, maqueta 46):
 *
 * - **Sen ningún concello:** a portada con instalacións de exemplo («Reserva a túa pista», buscador
 *   desactivado). A administración da plataforma, ao volver de entrar, vai á alta do concello.
 * - **Cun só concello:** a súa portada (`/<slug>`), sen lista que escoller.
 * - **Con varios:** «Escolle o teu concello», como ata agora.
 */
@Component({
  selector: 'furar-municipality-picker-page',
  imports: [
    RouterLink,
    TranslocoPipe,
    MunicipalityHomePage,
    PageHeaderComponent,
    ProblemAlertComponent,
    RowListDirective,
    RowComponent,
    StatusStateComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @switch (view()) {
      @case ('showcase') {
        <furar-municipality-home-page showcase />
      }
      @case ('list') {
        <div class="f-wrap f-home__section">
          <div class="max-w-col mx-auto">
            <furar-page-header
              size="display"
              [heading]="'picker.title' | transloco"
              [intro]="'picker.intro' | transloco"
            />
            <ul furarRowList [attr.aria-label]="'picker.listLabel' | transloco">
              @for (municipality of municipalities(); track municipality.id) {
                <li><furar-row [label]="municipality.name" [link]="['/', municipality.slug]" /></li>
              }
            </ul>
            @if (platform.platformAdmin()) {
              <p class="mt-section">
                <a [routerLink]="newMunicipality">{{ 'platform.createLink' | transloco }}</a>
              </p>
            }
          </div>
        </div>
      }
      @default {
        <div class="f-wrap f-home__section">
          <div class="max-w-col mx-auto">
            <furar-problem-alert [problem]="problem()" />
            @if (!problem()) {
              <furar-status-state mode="loading" />
            }
          </div>
        </div>
      }
    }
  `,
})
export class MunicipalityPickerPage implements OnInit, OnDestroy {
  private readonly store = inject(MunicipalityStore);
  private readonly router = inject(Router);
  private readonly session = inject(SessionService);
  protected readonly platform = inject(PlatformAccessService);
  private readonly firstRun = inject(FirstRun);
  protected readonly municipalities = signal<MunicipalitySummaryResponse[]>([]);
  protected readonly view = signal<RootView>('loading');
  protected readonly problem = signal<ApiProblem | null>(null);
  protected readonly newMunicipality = NEW_MUNICIPALITY_URL;

  ngOnInit(): void {
    void this.initialize();
  }

  ngOnDestroy(): void {
    this.firstRun.set(false);
  }

  private async initialize(): Promise<void> {
    let list: MunicipalitySummaryResponse[];
    try {
      list = await this.store.list();
    } catch (error: unknown) {
      this.problem.set(asProblem(error));
      return;
    }
    const only = list.length === 1 ? list[0] : undefined;
    if (only) {
      await this.router.navigate(['/', only.slug], { replaceUrl: true });
      return;
    }
    this.firstRun.set(list.length === 0);
    if (list.length === 0) {
      // A primeira petición coa conta de FURAR_ADMIN_EMAIL dálle o rol (ADR-0028).
      await this.session.whenReady();
      if (this.session.authenticated() && (await this.platform.load())) {
        await this.router.navigateByUrl(NEW_MUNICIPALITY_URL, { replaceUrl: true });
        return;
      }
      this.view.set('showcase');
      return;
    }
    this.municipalities.set(list);
    this.view.set('list');
  }
}
