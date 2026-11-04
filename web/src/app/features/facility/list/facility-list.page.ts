// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { OnInit } from '@angular/core';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { skip } from 'rxjs';
import type { ApiProblem } from '../../../core/errors/api-problem';
import { ToastService } from '../../../shared/ui/toast/toast.service';
import { CurrentMunicipality } from '../../../core/municipality/current-municipality';
import { ButtonDirective } from '../../../shared/ui/button/button.directive';
import { DialogService } from '../../../shared/ui/dialog/dialog.service';
import { asProblem } from '../../../shared/ui/edit-section/section-errors';
import { IconComponent } from '../../../shared/ui/icon/icon.component';
import type { ChipMenuOption } from '../../../shared/ui/menu/chip-menu.component';
import { ChipMenuComponent } from '../../../shared/ui/menu/chip-menu.component';
import { PageHeaderComponent } from '../../../shared/ui/page-header/page-header.component';
import { ProblemAlertComponent } from '../../../shared/ui/problem-alert.component';
import { MediaCardComponent } from '../../../shared/ui/media-card/media-card.component';
import { localized } from '../../../shared/custom-fields/localized';
import { LanguageService } from '../../../core/i18n/language.service';
import { StatusStateComponent } from '../../../shared/ui/status-state/status-state.component';
import { TagComponent } from '../../../shared/ui/tag/tag.component';
import { AffectedBookingsFlow } from '../affected-bookings/affected-bookings.flow';
import type { FacilityStatus, FacilitySummary } from './facility.store';
import { cardAddress } from './card-address';
import { FacilityStore } from './facility.store';
import { STEP_COUNT, resumeStep } from '../wizard/wizard';
import { DeploymentSettings } from '../../../core/deployment/deployment-settings';

/** Ton de cada estado no chip (o mesmo ca na ficha): o estado vai sempre en texto. */
const TONES: Record<FacilityStatus, ChipMenuOption['tone']> = {
  ACTIVE: 'success',
  TEMPORARILY_CLOSED: 'warn',
  WITHDRAWN: 'muted',
};

/** Parámetro do URL que abre a alta ao entrar (`?nova=1`). */
const NEW_PARAM = 'nova';

export { cardAddress };

/**
 * Listado de instalacións do xestor (D1-20; especificación, bloque 1, «Listado»): tarxetas con
 * imaxe (`furar-media-card`, as mesmas da portada) na orde do servidor, cada unha enteira ligazón
 * á ficha, cos deportes e co chip de estado como botón aparte. Activa e Pechada aplícanse ao momento; «De baixa» pide
 * confirmación (P17); con reservas afectadas, o modal de D1-19. «Borrador» ao lado do chip
 * mentres lle falte algo para publicarse (P15). Un borrador sen publicar leva «Borrador» e o paso
 * onde segue («Paso 5 de 8») no canto do chip, e abre o asistente. «Nova instalación» (ou `?nova=1`
 * no URL) abre o asistente.
 */
@Component({
  selector: 'furar-facility-list-page',
  imports: [
    ButtonDirective,
    ChipMenuComponent,
    IconComponent,
    PageHeaderComponent,
    ProblemAlertComponent,
    MediaCardComponent,
    StatusStateComponent,
    TagComponent,
    TranslocoPipe,
  ],
  providers: [FacilityStore],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './facility-list.page.html',
})
export class FacilityListPage implements OnInit {
  protected readonly store = inject(FacilityStore);
  protected readonly address = cardAddress;
  private readonly language = inject(LanguageService);
  protected readonly problem = signal<ApiProblem | null>(null);
  /** Instalación á que se lle está cambiando o estado. */
  protected readonly changing = signal<string | null>(null);

  private readonly municipality = inject(CurrentMunicipality);
  private readonly transloco = inject(TranslocoService);
  private readonly lang = toSignal(this.transloco.langChanges$);
  private readonly dialogs = inject(DialogService);
  private readonly affected = inject(AffectedBookingsFlow);
  private readonly toast = inject(ToastService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly mapEnabled = inject(DeploymentSettings).mapEnabled;
  protected readonly stepCount = STEP_COUNT;

  protected readonly statusOptions = computed<ChipMenuOption<FacilityStatus>[]>(() => {
    this.lang();
    return (Object.keys(TONES) as FacilityStatus[]).map((value) => ({
      value,
      label: this.statusLabel(value),
      tone: TONES[value],
    }));
  });

  constructor() {
    // A orde é a do idioma de quen mira: ao cambialo, volve pedir a lista sen baleirala.
    this.transloco.langChanges$.pipe(skip(1), takeUntilDestroyed()).subscribe(() => void this.load(true));
  }

  ngOnInit(): void {
    void this.load();
    // `?nova=1` (a ligazón «Crear» de «Para abrir as reservas»): abre o asistente, sen deixar o
    // listado con `?nova=1` no historial.
    if (this.route.snapshot.queryParamMap.has(NEW_PARAM)) {
      void this.router.navigate(['nova'], { relativeTo: this.route, replaceUrl: true });
    }
  }

  /** Texto alternativo da portada no idioma de quen mira. */
  protected coverAlt(facility: FacilitySummary): string {
    return localized(facility.cover?.alt, this.language.lang());
  }

  protected sportName(sport: FacilitySummary['sports'][number]): string {
    return localized(sport.label, this.language.lang());
  }

  protected async load(quiet = false): Promise<void> {
    const slug = this.municipality.slug();
    if (slug) await this.store.load(slug, { quiet });
  }

  /** Activa e Pechada directamente; «De baixa» confirma antes (P17). */
  protected async changeStatus(facility: FacilitySummary, status: FacilityStatus): Promise<void> {
    const slug = this.municipality.slug();
    if (!slug) return;
    this.problem.set(null);
    if (status === 'WITHDRAWN' && !(await this.confirmWithdraw(facility))) return;
    this.changing.set(facility.id);
    const params = { name: facility.name, status: this.statusLabel(status) };
    try {
      const outcome = await this.affected.run(
        (decisions) => this.store.changeStatus(slug, facility, status, decisions),
        { summary: this.transloco.translate('facility.list.statusSummary', params) },
      );
      if (outcome.done) this.toast.show('facility.list.statusChanged', params);
    } catch (error) {
      const problem = asProblem(error);
      this.problem.set(problem);
      // Outra persoa cambiouna ou xa non está: tráese o listado ao día sen perder a páxina.
      if (problem.status === 412 || problem.status === 404) await this.load(true);
    } finally {
      this.changing.set(null);
    }
  }

  /** «Nova instalación»: o asistente, nunha páxina. */
  protected openCreate(): void {
    void this.router.navigate(['nova'], { relativeTo: this.route });
  }

  /** Un borrador sen publicar abre o asistente; o resto, a ficha. */
  protected link(facility: FacilitySummary): string[] {
    return facility.published ? [facility.id] : ['nova', facility.id];
  }

  /** «Paso N de 8» dun borrador: onde segue o asistente. */
  protected draftStep(facility: FacilitySummary): number {
    return resumeStep(facility, this.mapEnabled()) + 1;
  }

  private statusLabel(status: FacilityStatus): string {
    return this.transloco.translate(`facility.list.status.${status}`);
  }

  private confirmWithdraw(facility: FacilitySummary): Promise<boolean> {
    const t = (key: string) => this.transloco.translate(`facility.list.withdraw.${key}`, { name: facility.name });
    return this.dialogs.confirm({
      heading: t('title'),
      message: t('text'),
      confirmLabel: t('action'),
      tone: 'danger',
    });
  }
}
