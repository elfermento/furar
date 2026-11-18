// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import {
  ChangeDetectionStrategy,
  Component,
  Injector,
  computed,
  effect,
  inject,
  signal,
  untracked,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { Router } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { firstValueFrom } from 'rxjs';
import { LanguageService } from '../../../core/i18n/language.service';
import { ManageNav } from '../../../core/layout/manage-nav';
import { CurrentMunicipality } from '../../../core/municipality/current-municipality';
import { ButtonDirective } from '../../../shared/ui/button/button.directive';
import type { ColumnFilterOption } from '../../../shared/ui/column-filter/column-filter.component';
import { ColumnFilterComponent } from '../../../shared/ui/column-filter/column-filter.component';
import { DialogService } from '../../../shared/ui/dialog/dialog.service';
import { focusPageHeadingIfLost } from '../../../shared/ui/focus-page-heading';
import { IconComponent } from '../../../shared/ui/icon/icon.component';
import { PageHeaderComponent } from '../../../shared/ui/page-header/page-header.component';
import type { StatusCardLine } from '../../../shared/ui/status-card/status-card.component';
import { StatusCardComponent } from '../../../shared/ui/status-card/status-card.component';
import { StatusStateComponent } from '../../../shared/ui/status-state/status-state.component';
import { TagComponent } from '../../../shared/ui/tag/tag.component';
import { ToastService } from '../../../shared/ui/toast/toast.service';
import { agoText } from '../manage/manage-bookings';
import type { OrganizationDialogData, OrganizationDialogResult } from './organization-dialog.component';
import { OrganizationDialogComponent } from './organization-dialog.component';
import { ORGANIZATION_STATUSES, STATUS_TONE, placeParts, scheduleParts } from './seasons';
import type { OrganizationResponse, OrganizationRowResponse, SeriesRequestCardResponse } from './seasons.store';
import { SeasonsStore } from './seasons.store';

/** Unha tarxeta de solicitude: a solicitude e o que pinta. */
interface RequestCard {
  readonly request: SeriesRequestCardResponse;
  readonly lines: readonly StatusCardLine[];
  readonly ago: string;
}

let nextId = 0;

/**
 * «Entidades» (`/:concello/xestion/entidades`; ADR-0039): a pestana de «Reservas» da
 * xestión. Arriba «Nova entidade»; despois as solicitudes de tempada pendentes, unha tarxeta
 * pulsable por solicitude (a entidade; a instalación con «· parte · uso»; os días, as horas e as
 * datas; o chip «Pendente» e cando se pediu), que leva a revisala; e a táboa das entidades
 * (Entidade · Contacto · Teléfono · Responsables · Estado, co filtro do estado na cabeceira; no
 * móbil, filas co filtro en pílula). Picar nunha entidade abre o seu diálogo.
 */
@Component({
  selector: 'furar-organizations-page',
  imports: [
    ButtonDirective,
    ColumnFilterComponent,
    IconComponent,
    PageHeaderComponent,
    StatusCardComponent,
    StatusStateComponent,
    TagComponent,
    TranslocoPipe,
  ],
  providers: [SeasonsStore],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './organizations.page.html',
})
export class OrganizationsPage {
  protected readonly store = inject(SeasonsStore);
  private readonly municipality = inject(CurrentMunicipality);
  private readonly dialogs = inject(DialogService);
  private readonly injector = inject(Injector);
  private readonly router = inject(Router);
  private readonly toasts = inject(ToastService);
  private readonly language = inject(LanguageService);
  private readonly transloco = inject(TranslocoService);
  private readonly langChanges = toSignal(this.transloco.langChanges$);
  protected readonly underTabs = inject(ManageNav).underTabs;

  private readonly id = nextId++;
  protected readonly ids = { requests: `furar-orgs-${this.id}-requests` };
  protected readonly tone = STATUS_TONE;
  protected readonly selected = signal<readonly string[]>([]);
  protected readonly opening = signal(false);
  private readonly slug = computed(() => this.municipality.slug() ?? '');
  private readonly t = (key: string, params?: Record<string, unknown>) => this.transloco.translate(key, params);

  protected readonly rows = computed(() => {
    const selected = this.selected();
    const all = this.store.organizations();
    return selected.length ? all.filter((org) => selected.includes(org.status)) : all;
  });
  protected readonly statusOptions = computed<ColumnFilterOption[]>(() => {
    this.langChanges();
    const all = this.store.organizations();
    return ORGANIZATION_STATUSES.map((status) => ({
      value: status,
      label: this.t(`booking.seasons.organizations.status.${status}`),
      count: all.filter((org) => org.status === status).length,
      tone: STATUS_TONE[status],
    })).filter((option) => option.count > 0);
  });
  protected readonly requestCards = computed<RequestCard[]>(() => {
    this.langChanges();
    const lang = this.language.lang();
    return this.store.requests().map((request) => ({
      request,
      lines: [
        { id: 'place', icon: 'location', parts: placeParts(request) },
        { id: 'when', icon: 'event-repeat', parts: scheduleParts(request, lang, this.t) },
      ],
      ago: this.t('booking.seasons.request.requested', { ago: agoText(request.createdAt, lang) }),
    }));
  });

  constructor() {
    effect(() => {
      const slug = this.slug();
      if (slug) untracked(() => void this.store.loadOrganizations(slug));
    });
  }

  protected load(): void {
    void this.store.loadOrganizations(this.slug());
  }

  protected managersText(count: number): string {
    this.langChanges();
    if (count === 0) return this.t('booking.seasons.organizations.managersNone');
    return this.t(`booking.seasons.organizations.${count === 1 ? 'managersOne' : 'managersOther'}`, { count });
  }

  /** A liña de debaixo no móbil: o teléfono, o contacto e os responsables. */
  protected itemParts(org: OrganizationRowResponse): string[] {
    return [org.phone, org.contactName, this.managersText(org.managers)].filter((part): part is string => !!part);
  }

  protected toggle(value: string): void {
    this.selected.update((list) => (list.includes(value) ? list.filter((v) => v !== value) : [...list, value]));
  }

  protected review(request: SeriesRequestCardResponse): void {
    void this.router.navigate(['/', this.slug(), 'xestion', 'entidades', 'solicitudes', request.id]);
  }

  /**
   * Nova (`null`) ou editar: o diálogo coa entidade xa pedida (os responsables e a versión). Se a
   * entidade se eliminou, a súa fila (onde volvería o foco) xa non está: o foco vai ao título.
   */
  protected async open(row: OrganizationRowResponse | null): Promise<void> {
    if (this.opening()) return;
    this.opening.set(true);
    let organization: OrganizationResponse | null = null;
    try {
      if (row) organization = await this.store.getOrganization(this.slug(), row.id);
    } catch (error) {
      this.toasts.error('booking.seasons.organizations.loadOne', error);
      return;
    } finally {
      this.opening.set(false);
    }
    const data: OrganizationDialogData = { slug: this.slug(), organization };
    const ref = this.dialogs.open<OrganizationDialogResult, OrganizationDialogData, OrganizationDialogComponent>(
      OrganizationDialogComponent,
      { data, injector: this.injector },
    );
    const done = await firstValueFrom(ref.closed, { defaultValue: undefined });
    if (!done) return;
    this.toasts.show(done.key, done.params);
    focusPageHeadingIfLost(this.injector);
  }
}
