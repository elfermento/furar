// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { DOCUMENT } from '@angular/common';
import type { ElementRef, OnInit } from '@angular/core';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  Injector,
  afterNextRender,
  computed,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { firstValueFrom } from 'rxjs';
import { CurrentMunicipality } from '../../../core/municipality/current-municipality';
import { ButtonDirective } from '../../../shared/ui/button/button.directive';
import { DialogService } from '../../../shared/ui/dialog/dialog.service';
import { IconComponent, registerSymbols } from '../../../shared/ui/icon/icon.component';
import { LAZY_SYMBOLS } from '../../../shared/ui/icon/lazy-symbols';
import { StatusStateComponent } from '../../../shared/ui/status-state/status-state.component';
import { TabDirective, TabsComponent } from '../../../shared/ui/tabs/tabs.component';
import type { BookingDetailDialogData } from './booking-detail-dialog.component';
import { BookingDetailDialogComponent } from './booking-detail-dialog.component';
import { ManagedBookingDetailComponent } from './booking-detail.component';
import type { BookingFiltersData } from './booking-filters-dialog.component';
import { BookingFiltersDialogComponent } from './booking-filters-dialog.component';
import { BookingRowsComponent } from './booking-rows.component';
import { BookingsToolbarComponent } from './bookings-toolbar.component';
import { linkedBooking } from './linked-booking';
import { ManageHeadingComponent } from './manage-heading.component';
import type { BookingFilters, ListOrigin, ManagedBookingResponse, PaymentsTab } from './manage-bookings';
import {
  NO_FILTERS,
  PAYMENTS_TABS,
  activeCount,
  activeFilters,
  filtersFor,
  listParams,
  withoutFilter,
} from './manage-bookings';
import { madridToday } from '../../../shared/ui/calendar/madrid-time';
import { ManageBookingsStore } from './manage-bookings.store';
import { fadeIn, slideFrom, slideOut } from '../../../shared/ui/motion/motion';

// As iconas desta páxina que non van na carga inicial (Filtros, cobros, desfacer).
registerSymbols(LAZY_SYMBOLS);

/** A partir deste ancho o detalle vai ao lado (2/3 e 1/3); por debaixo, nun diálogo. */
const DESKTOP_QUERY = '(min-width: 64rem)';

/** Sección da portada da que se abriu o detalle (pasa a ocupar os 2/3) ou a lista. */
type Section = 'latest' | 'payments' | 'list';

let nextId = 0;

/**
 * «Reservas» do concello (`/:concello/xestion/reservas`, D2-15, maqueta 26), con «Nova reserva»
 * (maqueta 58). Busca, «Filtros» (diálogo; folla no móbil) e «Calendario» (D2-16). A portada ten «Últimas reservas» e «Cobros»
 * (pendentes de pago, a devolver e canceladas, coas contas); picar nunha reserva pasa a súa
 * sección aos 2/3 e trae o detalle ao 1/3 (fixo ao baixar; picar de novo, Esc ou fóra péchao),
 * cunha animación suave que respecta `prefers-reduced-motion`. «Ver máis», os filtros ou a busca
 * levan á lista (2/3, por páxinas, cos filtros en chips) co detalle ao lado. No móbil, o detalle
 * nun diálogo. A xestión e o persoal do mostrador (`bookingsGuard`); cancelar, só a xestión.
 * `?reserva=<id>` (a ligazón do rexistro de actividade) abre ao entrar o detalle desa reserva coma
 * se se picase nas últimas.
 */
@Component({
  selector: 'furar-manage-bookings-page',
  imports: [
    BookingRowsComponent,
    BookingsToolbarComponent,
    ButtonDirective,
    IconComponent,
    ManagedBookingDetailComponent,
    ManageHeadingComponent,
    StatusStateComponent,
    TabDirective,
    TabsComponent,
    TranslocoPipe,
  ],
  providers: [ManageBookingsStore],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './manage-bookings.page.html',
})
export class ManageBookingsPage implements OnInit {
  protected readonly store = inject(ManageBookingsStore);
  private readonly municipality = inject(CurrentMunicipality);
  private readonly dialogs = inject(DialogService);
  private readonly transloco = inject(TranslocoService);
  private readonly injector = inject(Injector);
  private readonly document = inject(DOCUMENT);
  private readonly view = this.document.defaultView;
  private readonly langChanges = toSignal(this.transloco.langChanges$);

  private readonly latestSection = viewChild<ElementRef<HTMLElement>>('latestSection');
  private readonly paymentsSection = viewChild<ElementRef<HTMLElement>>('paymentsSection');
  private readonly detailCard = viewChild<ElementRef<HTMLElement>>('detailCard');

  private readonly id = nextId++;
  /** A reserva que abrir ao entrar (`?reserva=`). */
  private readonly linked = linkedBooking();
  protected readonly ids = {
    latest: `furar-manage-${this.id}-latest`,
    payments: `furar-manage-${this.id}-payments`,
    list: `furar-manage-${this.id}-list`,
  };
  protected readonly tabs = PAYMENTS_TABS;
  protected readonly today = madridToday();
  protected readonly slug = computed(() => this.municipality.slug() ?? '');

  /** `null`: a portada; se non, a lista e de onde vén (o seu título). */
  protected readonly origin = signal<ListOrigin | null>(null);
  protected readonly filters = signal<BookingFilters>(NO_FILTERS);
  protected readonly q = signal('');
  protected readonly tab = signal<PaymentsTab>('pendingPayment');
  /** De onde se abriu o detalle. */
  protected readonly chosen = signal<Section | null>(null);
  protected readonly desktop = signal(this.view?.matchMedia?.(DESKTOP_QUERY).matches ?? true);
  /** A fila picada (a súa instalación, mentres chega o detalle). */
  private readonly selectedRow = signal<ManagedBookingResponse | null>(null);

  protected readonly selectedId = computed(() => this.store.detail()?.id ?? null);
  /** Na portada, o detalle ao lado só no escritorio e cunha reserva aberta. */
  protected readonly inlineDetail = computed(() => this.desktop() && this.store.detail() !== null);
  protected readonly filterCount = computed(() => activeCount(this.filters()));
  /** A instalación da reserva aberta (miniatura do detalle). */
  protected readonly selectedFacility = computed(() => {
    const facilityId = this.store.detail()?.detail?.facilityId ?? this.selectedRow()?.facilityId;
    return this.store.facilities().find((f) => f.id === facilityId) ?? null;
  });
  /** Os filtros postos, un chip por valor, coa ✕ para quitalo. */
  protected readonly activeChips = computed(() => {
    this.langChanges();
    return activeFilters(this.filters(), this.store.facilities(), this.today, (key, params) =>
      this.transloco.translate(`booking.manage.filters.${key}`, params),
    );
  });

  constructor() {
    const query = this.view?.matchMedia?.(DESKTOP_QUERY);
    const onChange = (event: MediaQueryListEvent) => this.desktop.set(event.matches);
    query?.addEventListener?.('change', onChange);
    // Esc ou picar fóra pechan o detalle ao lado (o diálogo do móbil xa o fai el só).
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape' && this.besideAndOutside(event, '.cdk-overlay-container')) this.closeDetail();
    };
    const onDown = (event: MouseEvent) => {
      if (this.besideAndOutside(event, '[data-detail-card], .f-mrow, .cdk-overlay-container')) this.closeDetail();
    };
    this.document.addEventListener('keydown', onKey);
    this.document.addEventListener('mousedown', onDown);
    inject(DestroyRef).onDestroy(() => {
      query?.removeEventListener?.('change', onChange);
      this.document.removeEventListener('keydown', onKey);
      this.document.removeEventListener('mousedown', onDown);
    });
  }

  ngOnInit(): void {
    const slug = this.slug();
    if (!slug) return;
    void this.store.loadOverview(slug);
    void this.store.loadFacilities(slug);
    if (!this.linked) return;
    this.chosen.set('latest');
    void this.store.open(slug, this.linked);
    if (!this.desktop()) this.openDetailDialog();
  }

  protected retry(): void {
    void this.store.loadOverview(this.slug());
  }

  /** Buscar leva á lista («Resultados»); baleirar a busca sen filtros volve á portada. */
  protected search(value: string): void {
    this.q.set(value);
    const origin = this.origin();
    if (value.trim()) this.showList(origin ?? 'results');
    else if (origin === 'results' && activeCount(this.filters()) === 0) this.goHome();
    else if (origin !== null) this.refreshList();
  }

  /** «Ver máis»: a lista da sección, cos mesmos criterios cos que a API a conta. */
  protected openList(origin: ListOrigin): void {
    this.filters.set(filtersFor(origin, this.today));
    this.showList(origin);
  }

  protected async openFilters(): Promise<void> {
    const data: BookingFiltersData = {
      filters: this.filters(),
      facilities: this.store.facilities().map((f) => ({ id: f.id, name: f.name })),
      today: this.today,
    };
    const ref = this.dialogs.open<BookingFilters, BookingFiltersData, BookingFiltersDialogComponent>(
      BookingFiltersDialogComponent,
      { data, sheet: true },
    );
    const filters = await firstValueFrom(ref.closed, { defaultValue: undefined });
    if (!filters) return;
    this.filters.set(filters);
    const plain = activeCount(filters) === 0 && !this.q().trim() && filters.sort === 'START';
    this.showList(plain ? 'upcoming' : 'results');
  }

  protected removeFilter(id: string): void {
    this.filters.update((filters) => withoutFilter(filters, id));
    this.showList('results');
  }

  protected goHome(): void {
    this.closeDetail(false);
    this.origin.set(null);
    this.filters.set(NO_FILTERS);
    this.q.set('');
    this.store.clearList();
  }

  protected more(): void {
    void this.store.loadMore(this.params());
  }

  protected refreshList(): void {
    void this.store.loadList(this.params());
  }

  /**
   * Picar nunha reserva: ábrea (ou péchaa se xa estaba aberta). Na portada, a súa sección pasa aos
   * 2/3 cun desprazamento suave; no móbil, o detalle nun diálogo.
   */
  protected select(booking: ManagedBookingResponse, section: Section): void {
    if (this.selectedId() === booking.id && this.chosen() === section) {
      this.closeDetail();
      return;
    }
    const wasOpen = this.store.detail() !== null;
    const element = this.sectionElement(section);
    const before = element?.getBoundingClientRect();
    this.selectedRow.set(booking);
    this.chosen.set(section);
    void this.store.open(this.slug(), booking.id);
    if (!this.desktop()) {
      this.openDetailDialog();
    } else if (!wasOpen && element && before) {
      afterNextRender(() => slideFrom(this.view, element, before), { injector: this.injector });
    }
  }

  /** Pecha o detalle: a tarxeta vaise e as seccións volven ao seu sitio. */
  protected closeDetail(animated = true): void {
    if (this.store.detail() === null) return;
    const finish = () => {
      const section = this.chosen();
      const element = section ? this.sectionElement(section) : undefined;
      const before = element?.getBoundingClientRect();
      this.reset();
      if (this.origin() !== null || !element || !before) return;
      afterNextRender(
        () => {
          slideFrom(this.view, element, before);
          for (const other of [this.latestSection(), this.paymentsSection()]) {
            if (other && other.nativeElement !== element) fadeIn(this.view, other.nativeElement);
          }
        },
        { injector: this.injector },
      );
    };
    if (animated && this.origin() === null) slideOut(this.view, this.detailCard()?.nativeElement, finish);
    else finish();
  }

  private reset(): void {
    this.store.close();
    this.chosen.set(null);
    this.selectedRow.set(null);
  }

  private showList(origin: ListOrigin): void {
    if (this.origin() === null) this.closeDetail(false);
    this.origin.set(origin);
    this.refreshList();
  }

  private params() {
    return listParams(this.slug(), this.filters(), this.q(), this.today);
  }

  private openDetailDialog(): void {
    const data: BookingDetailDialogData = { facility: this.selectedFacility() };
    const ref = this.dialogs.open(BookingDetailDialogComponent, { data, injector: this.injector });
    ref.closed.subscribe(() => this.reset());
  }

  private sectionElement(section: Section): HTMLElement | undefined {
    if (section === 'latest') return this.latestSection()?.nativeElement;
    if (section === 'payments') return this.paymentsSection()?.nativeElement;
    return undefined;
  }

  /**
   * O detalle está ao lado e o evento vén de fóra de `selector`. Esc pecha tamén co foco na fila que
   * o abriu ou dentro del (só teclado), pero non se vén dunha capa (diálogo, menú), que se pecha
   * ela; picar pecha se é fóra del, das filas e de calquera capa.
   */
  private besideAndOutside(event: Event, selector: string): boolean {
    if (!this.desktop() || this.store.detail() === null) return false;
    return !(event.target as Element | null)?.closest?.(selector);
  }
}
