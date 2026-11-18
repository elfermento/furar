// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, computed, effect, inject, signal, untracked } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { StaffAccessService } from '../../core/auth/staff-access.service';
import { ManageNav } from '../../core/layout/manage-nav';
import { CurrentMunicipality } from '../../core/municipality/current-municipality';
import { eventWhen, inLang } from '../../shared/events/event-format';
import { ButtonDirective } from '../../shared/ui/button/button.directive';
import type { ColumnFilterOption } from '../../shared/ui/column-filter/column-filter.component';
import { ColumnFilterComponent } from '../../shared/ui/column-filter/column-filter.component';
import { DialogService } from '../../shared/ui/dialog/dialog.service';
import { IconComponent } from '../../shared/ui/icon/icon.component';
import { MenuDirective, MenuItemDirective, MenuTriggerDirective } from '../../shared/ui/menu/menu.directives';
import { PageHeaderComponent } from '../../shared/ui/page-header/page-header.component';
import type { SelectOption } from '../../shared/ui/select/select.component';
import { StatusStateComponent } from '../../shared/ui/status-state/status-state.component';
import { TagComponent } from '../../shared/ui/tag/tag.component';
import type { EventStatus } from './events';
import {
  EVENT_KINDS,
  EVENT_STATUSES,
  STATUS_LOOK,
  STATUS_TONE,
  filterEvents,
  kindCounts,
  seatsText,
  statusCounts,
  statusOf,
  whenLines,
} from './events';
import type { EventResponse } from './events.store';
import { EventsStore } from './events.store';

/**
 * «Eventos e actividades» na xestión: táboa coas regras de Furar (filtros de «Tipo» e
 * «Estado» nas súas cabeceiras; no móbil, a pílula do estado enriba das tarxetas). A fila abre o
 * detalle (un diálogo grande con pestanas) e o seu menú «⋯», «Editar» e «Duplicar». «Novo» abre a
 * alta. Recepción le a lista e o detalle sen cambiar nada. Con `?ver=<id>` (desde o calendario),
 * ábrese ese detalle.
 */
@Component({
  selector: 'furar-events-page',
  imports: [
    ButtonDirective,
    ColumnFilterComponent,
    IconComponent,
    MenuDirective,
    MenuItemDirective,
    MenuTriggerDirective,
    PageHeaderComponent,
    StatusStateComponent,
    TagComponent,
    TranslocoPipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './events.page.html',
})
export class EventsPage {
  protected readonly store = inject(EventsStore);
  private readonly municipality = inject(CurrentMunicipality);
  private readonly dialogs = inject(DialogService);
  private readonly transloco = inject(TranslocoService);
  private readonly staff = inject(StaffAccessService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly lang = toSignal(this.transloco.langChanges$, { initialValue: this.transloco.getActiveLang() });
  protected readonly underTabs = inject(ManageNav).underTabs;

  protected readonly tone = STATUS_TONE;
  protected readonly look = STATUS_LOOK;
  protected readonly statusOf = statusOf;
  protected readonly selected = signal<readonly string[]>([]);
  protected readonly kinds = signal<readonly string[]>([]);
  protected readonly opening = signal(false);
  /** Alta, edición e copia (`event.manage`): Xestión. */
  protected readonly canManage = computed(() => this.staff.role() === 'MANAGER');

  protected readonly rows = computed(() => filterEvents(this.store.items(), this.selected(), this.kinds()));
  protected readonly statusOptions = computed<ColumnFilterOption[]>(() => {
    this.lang();
    const counts = statusCounts(this.store.items());
    return EVENT_STATUSES.filter((status) => counts.has(status)).map((status) => ({
      value: status,
      label: this.transloco.translate('event.status.' + status),
      count: counts.get(status) ?? 0,
      tone: STATUS_TONE[status],
    }));
  });
  protected readonly kindOptions = computed<ColumnFilterOption[]>(() => {
    this.lang();
    const counts = kindCounts(this.store.items());
    return EVENT_KINDS.filter((kind) => counts.has(kind)).map((kind) => ({
      value: kind,
      label: this.transloco.translate('event.kind.' + kind),
      count: counts.get(kind) ?? 0,
    }));
  });

  private wanted = this.route.snapshot.queryParamMap.get('ver');

  constructor() {
    effect(() => {
      const slug = this.municipality.slug();
      if (slug) untracked(() => void this.load(slug));
    });
  }

  protected name(event: EventResponse): string {
    this.lang();
    return inLang(event.name, this.transloco.getActiveLang());
  }

  protected when(event: EventResponse): { main: string; sub: string | null } {
    const lang = this.lang();
    return whenLines(
      event,
      lang,
      (key, params) => this.transloco.translate(key, params),
      (startsAt) => eventWhen(startsAt, lang, true),
    );
  }

  protected place(event: EventResponse): string {
    return event.facility?.name ?? event.place ?? '';
  }

  protected seats(event: EventResponse): string {
    this.lang();
    return seatsText(event, (key, params) => this.transloco.translate(key, params));
  }

  protected statusLabel(status: EventStatus): string {
    this.lang();
    return this.transloco.translate('event.status.' + status);
  }

  protected kindLabel(event: EventResponse): string {
    this.lang();
    return this.transloco.translate('event.kind.' + event.kind);
  }

  protected toggle(list: 'status' | 'kind', value: string): void {
    const target = list === 'status' ? this.selected : this.kinds;
    target.update((items) => (items.includes(value) ? items.filter((v) => v !== value) : [...items, value]));
  }

  protected retry(): void {
    const slug = this.municipality.slug();
    if (slug) void this.load(slug);
  }

  /** «Novo» (sen evento), «Editar» ou «Duplicar» (`copy`). */
  protected async edit(event: EventResponse | null, copy = false): Promise<void> {
    const slug = this.municipality.slug();
    if (!slug || this.opening()) return;
    this.opening.set(true);
    let dialog: Promise<unknown>;
    try {
      const [{ openEventDialog }, facilities] = await Promise.all([
        import('./event-dialog.component'),
        this.facilities(slug),
      ]);
      dialog = openEventDialog(this.dialogs, { slug, event, copy, facilities });
    } finally {
      // O botón só espera mentres se prepara o diálogo, non mentres está aberto.
      this.opening.set(false);
    }
    await dialog;
  }

  /** O detalle (diálogo grande con pestanas). */
  protected async open(event: EventResponse): Promise<void> {
    const slug = this.municipality.slug();
    if (!slug || this.opening()) return;
    this.opening.set(true);
    let dialog: Promise<void>;
    try {
      const [{ openEventDetail }, facilities] = await Promise.all([
        import('./event-detail-dialog.component'),
        this.facilities(slug),
      ]);
      dialog = openEventDetail(this.dialogs, { slug, event, facilities });
    } finally {
      this.opening.set(false);
    }
    await dialog;
  }

  private async load(slug: string): Promise<void> {
    await this.store.load(slug);
    const wanted = this.wanted;
    if (!wanted) return;
    this.wanted = null;
    void this.router.navigate([], { relativeTo: this.route, queryParams: { ver: null }, replaceUrl: true });
    const event =
      this.store.items().find((item) => item.id === wanted) ?? (await this.store.get(slug, wanted).catch(() => null));
    if (event) await this.open(event);
  }

  /** As instalacións, só para quen pode editar (a lista de xestión). */
  private facilities(slug: string): Promise<SelectOption<string>[]> {
    return this.canManage() ? this.store.facilities(slug).catch(() => []) : Promise.resolve([]);
  }
}
