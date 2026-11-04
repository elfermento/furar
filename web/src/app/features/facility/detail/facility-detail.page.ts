// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { DOCUMENT } from '@angular/common';
import type { OnInit } from '@angular/core';
import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  Injector,
  afterNextRender,
  computed,
  inject,
  input,
  signal,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import type { ApiProblem } from '../../../core/errors/api-problem';
import { CurrentMunicipality } from '../../../core/municipality/current-municipality';
import { ButtonDirective } from '../../../shared/ui/button/button.directive';
import { CalloutComponent } from '../../../shared/ui/callout/callout.component';
import { IconComponent } from '../../../shared/ui/icon/icon.component';
import type { ChipMenuOption } from '../../../shared/ui/menu/chip-menu.component';
import { ChipMenuComponent } from '../../../shared/ui/menu/chip-menu.component';
import { PageHeaderComponent } from '../../../shared/ui/page-header/page-header.component';
import { ProblemAlertComponent } from '../../../shared/ui/problem-alert.component';
import { StatusStateComponent } from '../../../shared/ui/status-state/status-state.component';
import { asProblem, focusFirstError } from '../../../shared/ui/edit-section/section-errors';
import { ActionBarComponent } from '../../../shared/ui/action-bar/action-bar.component';
import { DeleteButtonComponent } from '../../../shared/ui/delete-button/delete-button.component';
import { EditSession } from '../../../shared/ui/edit-session/edit-session';
import { ToastService } from '../../../shared/ui/toast/toast.service';
import { PhotosDraft } from '../photos/photos-draft';
import { DialogService } from '../../../shared/ui/dialog/dialog.service';
import { AffectedBookingsFlow } from '../affected-bookings/affected-bookings.flow';
import { AmenitiesSectionComponent } from '../amenities/amenities-section.component';
import { BlockListComponent } from '../blocks/block-list.component';
import { DataCardComponent } from '../data-card/data-card.component';
import { DescriptionSectionComponent } from '../description/description-section.component';
import { HouseRulesSectionComponent } from '../description/house-rules-section.component';
import { PaymentNotesSectionComponent } from '../description/payment-notes-section.component';
import { cardAddress } from '../list/card-address';
import { PaymentNotesStore } from '../description/payment-notes.store';
import { LocationSectionComponent } from '../location/location-section.component';
import { PhotoGalleryComponent } from '../photos/photo-gallery.component';
import { ScheduleSectionComponent } from '../schedule/schedule-section.component';
import { UseListComponent } from '../uses/use-list.component';
import { DraftNoticeComponent } from './draft-notice.component';
import type { Facility, FacilityStatus } from './facility-detail.store';
import { FacilityDetailStore } from './facility-detail.store';
import { SECTION_IDS } from './sections';

/** Ton e texto do chip de cada estado (D1-05a): o estado vai sempre en texto. */
const STATUS: Record<FacilityStatus, { readonly tone: ChipMenuOption['tone']; readonly key: string }> = {
  ACTIVE: { tone: 'success', key: 'facility.detail.status.ACTIVE' },
  TEMPORARILY_CLOSED: { tone: 'warn', key: 'facility.detail.status.TEMPORARILY_CLOSED' },
  WITHDRAWN: { tone: 'muted', key: 'facility.detail.status.WITHDRAWN' },
};

/** Enderezo da cabeceira: «Rúa do Alto, 12 · 15570 Narón». */
export function shortAddress(facility: Facility): string {
  return cardAddress(facility);
}

/**
 * Ficha da instalación (D1-21a; especificación, bloque 1): «← Volver», fotos, cabeceira co nome e o
 * chip de estado na mesma liña, o enderezo coa icona de ubicación e «Editar» e «Eliminar»; aviso
 * de borrador e dúas columnas con **Datos primeiro no DOM** (no móbil, enriba; en escritorio, á
 * dereita sen cambiar a orde). Cada sección vive no seu contedor con `id` estable.
 *
 * **Modo edición global** (`EditSession`): «Editar» pon a ficha enteira en edición e unha barra
 * fixa (`furar-action-bar`) leva «Cancelar» (sae sen gardar nada; con cambios, pregunta antes) e
 * «Gardar», que garda cada parte con cambios co seu `If-Match`. **Eliminar**: só se nunca tivo reservas, escribindo a frase de
 * confirmación; borra a instalación coas súas imaxes da lista (as fotos seguen na biblioteca). Se
 * tivo reservas, explica que hai que dala de baixa para conservar o historial.
 */
@Component({
  selector: 'furar-facility-detail-page',
  imports: [
    ActionBarComponent,
    AmenitiesSectionComponent,
    BlockListComponent,
    ButtonDirective,
    CalloutComponent,
    ChipMenuComponent,
    DataCardComponent,
    DeleteButtonComponent,
    DescriptionSectionComponent,
    HouseRulesSectionComponent,
    PaymentNotesSectionComponent,
    DraftNoticeComponent,
    IconComponent,
    LocationSectionComponent,
    PageHeaderComponent,
    PhotoGalleryComponent,
    ProblemAlertComponent,
    RouterLink,
    ScheduleSectionComponent,
    StatusStateComponent,
    TranslocoPipe,
    UseListComponent,
  ],
  providers: [FacilityDetailStore, EditSession, PhotosDraft, PaymentNotesStore],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './facility-detail.page.html',
})
export class FacilityDetailPage implements OnInit {
  /** Parámetro `:id` da ruta (`withComponentInputBinding`). */
  readonly id = input.required<string>();

  protected readonly store = inject(FacilityDetailStore);
  protected readonly session = inject(EditSession);
  protected readonly saveFailed = signal(false);
  private readonly toast = inject(ToastService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  protected readonly ids = SECTION_IDS;
  protected readonly statusProblem = signal<ApiProblem | null>(null);
  protected readonly changingStatus = signal(false);
  protected readonly reloading = signal(false);
  protected readonly notFound = computed(() => this.store.problem()?.status === 404);
  protected readonly address = computed(() => {
    const facility = this.store.facility();
    return facility ? shortAddress(facility) : '';
  });
  protected readonly statusOptions = computed<ChipMenuOption<FacilityStatus>[]>(() => {
    this.lang();
    return (Object.keys(STATUS) as FacilityStatus[]).map((value) => ({
      value,
      label: this.transloco.translate(STATUS[value].key),
      tone: STATUS[value].tone,
    }));
  });

  private readonly municipality = inject(CurrentMunicipality);
  private readonly transloco = inject(TranslocoService);
  private readonly lang = toSignal(this.transloco.langChanges$);
  private readonly dialogs = inject(DialogService);
  private readonly affected = inject(AffectedBookingsFlow);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly document = inject(DOCUMENT);
  private readonly injector = inject(Injector);

  ngOnInit(): void {
    void this.load();
  }

  protected async load(): Promise<void> {
    const slug = this.municipality.slug();
    if (!slug) return;
    await this.store.load(slug, this.id());
    if (this.store.state() === 'ready') this.focusTitle();
  }

  protected async reload(): Promise<void> {
    this.reloading.set(true);
    try {
      await this.store.reload();
    } catch (error) {
      this.statusProblem.set(asProblem(error));
    } finally {
      this.reloading.set(false);
    }
  }

  protected startEditing(): void {
    this.statusProblem.set(null);
    this.saveFailed.set(false);
    this.session.start();
    this.focusIn('#facility-uses-title, h2');
  }

  /** Sae sen gardar; con cambios, pregunta antes. */
  protected async cancelEditing(): Promise<void> {
    if (!(await this.session.cancelAsking())) return;
    this.saveFailed.set(false);
    this.focusIn('[data-edit]');
  }

  protected async saveEditing(): Promise<void> {
    this.saveFailed.set(false);
    const result = await this.session.save();
    if (result === 'saved') {
      this.toast.show('facility.detail.edit.saved');
      this.focusIn('[data-edit]');
    } else if (result === 'invalid') {
      this.focusIn('[aria-invalid="true"]', true);
    } else {
      this.saveFailed.set(true);
      this.focusIn('[role="alert"] furar-callout', true);
    }
  }

  /** Só se nunca tivo reservas e escribindo a frase; se as tivo, explica a baixa. */
  protected async remove(): Promise<void> {
    const facility = this.store.facility();
    if (!facility) return;
    this.statusProblem.set(null);
    const t = (key: string) => this.transloco.translate(`facility.detail.delete.${key}`, { name: facility.name });
    if (!facility.deletable) {
      const withdraw = await this.dialogs.confirm({
        heading: t('bookedTitle'),
        message: t('bookedText'),
        confirmLabel: t('withdraw'),
        tone: 'danger',
      });
      if (withdraw) await this.changeStatus('WITHDRAWN', false);
      return;
    }
    try {
      const deleted = await this.dialogs.confirm({
        heading: t('title'),
        message: t('text'),
        confirmLabel: t('action'),
        busyLabel: t('busy'),
        tone: 'danger',
        typeToConfirm: t('phrase'),
        action: () => this.store.delete(),
      });
      if (!deleted) return;
      this.toast.show('facility.detail.delete.done', { name: facility.name });
      await this.router.navigate(['..'], { relativeTo: this.route });
    } catch (error) {
      this.statusProblem.set(asProblem(error));
    }
  }

  /** Activa e Pechada aplícanse directamente; «De baixa» pide confirmación (P17). */
  protected async changeStatus(status: FacilityStatus, confirm = true): Promise<void> {
    this.statusProblem.set(null);
    if (confirm && status === 'WITHDRAWN' && !(await this.confirmWithdraw())) return;
    this.changingStatus.set(true);
    try {
      await this.affected.run((decisions) => this.store.changeStatus(status, decisions));
    } catch (error) {
      this.statusProblem.set(asProblem(error));
    } finally {
      this.changingStatus.set(false);
    }
  }

  private confirmWithdraw(): Promise<boolean> {
    const t = (key: string) => this.transloco.translate(key);
    return this.dialogs.confirm({
      heading: t('facility.detail.withdraw.title'),
      message: t('facility.detail.withdraw.text'),
      confirmLabel: t('facility.detail.withdraw.action'),
      tone: 'danger',
    });
  }

  private focusIn(selector: string, error = false): void {
    afterNextRender(
      () => {
        const root = this.host.nativeElement;
        if (error && selector === '[aria-invalid="true"]') {
          focusFirstError(root);
          return;
        }
        const target = root.querySelector<HTMLElement>(selector);
        if (!target) return;
        if (!target.matches('button, a, input, select, textarea') && !target.hasAttribute('tabindex')) {
          target.tabIndex = -1;
        }
        target.focus();
      },
      { injector: this.injector },
    );
  }

  /**
   * Ao entrar, o foco vai ao `h1` (o shell xa o intenta ao navegar, pero a ficha aínda cargaba).
   * Non se move se quen usa a páxina xa puxo o foco noutro sitio.
   */
  private focusTitle(): void {
    afterNextRender(
      () => {
        const active = this.document.activeElement;
        const free = !active || active === this.document.body || active.tagName === 'MAIN';
        const heading = this.host.nativeElement.querySelector<HTMLElement>('h1');
        if (!free || !heading) return;
        heading.tabIndex = -1;
        heading.focus();
      },
      { injector: this.injector },
    );
  }
}
