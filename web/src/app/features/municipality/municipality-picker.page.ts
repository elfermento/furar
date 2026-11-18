// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { OnDestroy, OnInit } from '@angular/core';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import type { MunicipalitySummaryResponse } from './municipality.store';
import { PlatformAccessService } from '../../core/auth/platform-access.service';
import { NEW_MUNICIPALITY_URL } from '../../core/auth/roles';
import { SessionService } from '../../core/auth/session.service';
import type { ApiProblem } from '../../core/errors/api-problem';
import { asProblem } from '../../core/errors/problem.interceptor';
import { FirstRun } from '../../core/municipality/first-run';
import { MunicipalityDirectory } from '../../core/municipality/municipality-directory';
import { ProblemAlertComponent } from '../../shared/ui/problem-alert.component';
import { StatusStateComponent } from '../../shared/ui/status-state/status-state.component';
import { MunicipalityHomePage } from './municipality-home.page';

/** O que mostra a raíz segundo os concellos que hai. */
type RootView = 'loading' | 'showcase';

/**
 * Raíz (`/`) cando non toca a portada común (ADR-0028):
 *
 * - **Sen ningún concello:** a portada con instalacións de exemplo («Reserva a túa pista», buscador
 *   desactivado). A administración da plataforma, ao volver de entrar, vai á alta do concello.
 * - **Cun só concello:** a súa portada (`/<slug>`).
 * - **Con varios:** a portada común (ADR-0030), que é outra ruta da raíz
 *   (`severalMunicipalitiesMatch`); se a lista non se puido ler alí e agora si, vólvese entrar nela.
 */
@Component({
  selector: 'furar-municipality-picker-page',
  imports: [MunicipalityHomePage, ProblemAlertComponent, StatusStateComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @switch (view()) {
      @case ('showcase') {
        <furar-municipality-home-page showcase />
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
  private readonly directory = inject(MunicipalityDirectory);
  private readonly router = inject(Router);
  private readonly session = inject(SessionService);
  private readonly platform = inject(PlatformAccessService);
  private readonly firstRun = inject(FirstRun);
  protected readonly view = signal<RootView>('loading');
  protected readonly problem = signal<ApiProblem | null>(null);

  ngOnInit(): void {
    void this.initialize();
  }

  ngOnDestroy(): void {
    this.firstRun.set(false);
  }

  private async initialize(): Promise<void> {
    let list: readonly MunicipalitySummaryResponse[];
    try {
      // A ruta da portada común acaba de lela; se non puido, pídese agora.
      list = await this.directory.list();
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
    // A ruta da portada común non puido ler a lista e esta si: agora xa a ten gardada.
    await this.router.navigateByUrl('/', { onSameUrlNavigation: 'reload', replaceUrl: true });
  }
}
