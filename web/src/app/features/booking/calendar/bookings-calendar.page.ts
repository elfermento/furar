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
  effect,
  inject,
  signal,
  untracked,
  viewChild,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { LanguageService } from '../../../core/i18n/language.service';
import { CurrentMunicipality } from '../../../core/municipality/current-municipality';
import { ButtonDirective } from '../../../shared/ui/button/button.directive';
import type { IsoDate } from '../../../shared/ui/calendar/local-date';
import { weekdayNames } from '../../../shared/ui/calendar/local-date';
import { DialogService } from '../../../shared/ui/dialog/dialog.service';
import { IconComponent, registerSymbols } from '../../../shared/ui/icon/icon.component';
import { LAZY_SYMBOLS } from '../../../shared/ui/icon/lazy-symbols';
import type { SelectOption } from '../../../shared/ui/select/select.component';
import { SelectComponent } from '../../../shared/ui/select/select.component';
import {
  SegmentedOptionDirective,
  SegmentedRadioDirective,
} from '../../../shared/ui/segmented/segmented-radio.directive';
import { SegmentedDirective } from '../../../shared/ui/segmented/segmented.directive';
import { StatusStateComponent } from '../../../shared/ui/status-state/status-state.component';
import type { BookingDetailDialogData } from '../manage/booking-detail-dialog.component';
import { BookingDetailDialogComponent } from '../manage/booking-detail-dialog.component';
import { ManagedBookingDetailComponent } from '../manage/booking-detail.component';
import type { ManagedBookingResponse } from '../manage/manage-bookings';
import { madridToday } from '../../../shared/ui/calendar/madrid-time';
import { ManageBookingsStore } from '../manage/manage-bookings.store';
import { slideOut } from '../../../shared/ui/motion/motion';
import type { CalendarMode } from './bookings-calendar';
import { MODES, gridHours, periodLabel } from './bookings-calendar';
import type { PeekRequest, ViewText } from './bookings-calendar-view';
import { closedCells, gridBlocks, gridColumns, gridCounts, longDay, monthDays } from './bookings-calendar-view';
import { BookingsCalendarStore } from './bookings-calendar.store';
import { CalendarDayListComponent } from './calendar-day-list.component';
import { CalendarGridComponent } from './calendar-grid.component';
import { CalendarMonthComponent } from './calendar-month.component';
import { CalendarPeekComponent } from './calendar-peek.component';
import { CalendarPeriodComponent } from './calendar-period.component';

// As iconas do detalle que non van na carga inicial (cobros, desfacer).
registerSymbols(LAZY_SYMBOLS);

/** A partir deste ancho o detalle vai ao lado (2/3 e 1/3); por debaixo, nun diálogo. */
const DESKTOP_QUERY = '(min-width: 64rem)';
/** Por debaixo deste ancho, o día vai en lista (no móbil non cabe a grella). */
const NARROW_QUERY = '(max-width: 47.9375rem)';

/**
 * «Calendario» das reservas do concello (`/:concello/xestion/calendario`, D2-16, maqueta 27): volver
 * a «Reservas», a vista (Día · Semana · Mes) e a instalación (ou todas); debaixo, o período. Día:
 * unha columna por instalación; semana dunha instalación: un día por columna cos bloques; semana
 * de todas: a conta de cada hora; mes: a conta de cada día. Escoller unha instalación no día pasa
 * á semana. As contas abren a vista rápida; un bloque ou unha fila dela abren a mesma tarxeta da
 * reserva ca en «Reservas» (2/3 e 1/3; picar de novo, Esc ou fóra péchaa; no móbil, diálogo). No
 * móbil, o día vai en lista. As canceladas non ocupan.
 */
@Component({
  selector: 'furar-bookings-calendar-page',
  imports: [
    ButtonDirective,
    CalendarDayListComponent,
    CalendarGridComponent,
    CalendarMonthComponent,
    CalendarPeekComponent,
    CalendarPeriodComponent,
    IconComponent,
    SelectComponent,
    ManagedBookingDetailComponent,
    RouterLink,
    SegmentedDirective,
    SegmentedOptionDirective,
    SegmentedRadioDirective,
    StatusStateComponent,
    TranslocoPipe,
  ],
  providers: [ManageBookingsStore, BookingsCalendarStore],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './bookings-calendar.page.html',
})
export class BookingsCalendarPage implements OnInit {
  protected readonly manage = inject(ManageBookingsStore);
  protected readonly store = inject(BookingsCalendarStore);
  private readonly municipality = inject(CurrentMunicipality);
  private readonly dialogs = inject(DialogService);
  private readonly language = inject(LanguageService);
  private readonly transloco = inject(TranslocoService);
  private readonly injector = inject(Injector);
  private readonly document = inject(DOCUMENT);
  private readonly view = this.document.defaultView;
  private readonly langChanges = toSignal(this.transloco.langChanges$);
  private readonly detailCard = viewChild<ElementRef<HTMLElement>>('detailCard');
  protected readonly modes = MODES;
  protected readonly today = madridToday();
  protected readonly slug = computed(() => this.municipality.slug() ?? '');
  protected readonly mode = signal<CalendarMode>('DAY');
  protected readonly date = signal<IsoDate>(this.today);
  /** A instalación escollida ou `''` (todas). */
  protected readonly facilityId = signal('');
  protected readonly desktop = signal(this.view?.matchMedia?.(DESKTOP_QUERY).matches ?? true);
  protected readonly narrow = signal(this.view?.matchMedia?.(NARROW_QUERY).matches ?? false);
  /** O botón que abriu a vista rápida (o foco volve a el). */
  protected readonly peekAnchor = signal<HTMLElement | null>(null);
  /** A reserva picada (a súa instalación, mentres chega o detalle). */
  private readonly selectedRow = signal<ManagedBookingResponse | null>(null);

  protected readonly selectedId = computed(() => this.manage.detail()?.id ?? null);
  protected readonly inlineDetail = computed(() => this.desktop() && this.manage.detail() !== null);
  /** As instalacións do despregable (as que non están de baixa); «Todas» pona a plantilla. */
  protected readonly facilityOptions = computed((): SelectOption<string>[] =>
    this.manage.facilities().flatMap((f) => (f.status === 'WITHDRAWN' ? [] : [{ value: f.id, label: f.name }])),
  );
  protected readonly selectedFacility = computed(() => {
    const id = this.manage.detail()?.detail?.facilityId ?? this.selectedRow()?.facilityId;
    return this.manage.facilities().find((f) => f.id === id) ?? null;
  });
  private readonly text = computed<ViewText>(() => {
    this.langChanges();
    return {
      lang: this.language.lang(),
      translate: (key, params) => this.transloco.translate(key, params),
      today: this.today,
    };
  });
  protected readonly label = computed(() =>
    periodLabel(this.mode(), this.date(), this.text().lang, (key, params) =>
      this.text().translate(`booking.calendar.${key}`, params),
    ),
  );

  /** O calendario que chegou, se é o do que se ve agora. */
  protected readonly data = computed(() =>
    this.store.matching({ mode: this.mode(), date: this.date(), facilityId: this.facilityId() || null }),
  );
  protected readonly columns = computed(() =>
    gridColumns(
      this.mode(),
      this.date(),
      this.facilityId(),
      this.manage.facilities(),
      this.data()?.blocks ?? [],
      this.text(),
    ),
  );
  protected readonly hours = computed(() => gridHours(this.data(), this.columns()));
  protected readonly closed = computed(() => {
    const data = this.data();
    return data ? closedCells(data.openings, this.columns(), this.hours()) : new Set<string>();
  });
  protected readonly blocks = computed(() => {
    const data = this.data();
    return data ? gridBlocks(data.blocks, this.mode(), this.columns(), this.hours()[0], this.text()) : [];
  });
  protected readonly counts = computed(() => {
    const data = this.data();
    return data && !this.facilityId() ? gridCounts(data.hours, this.columns(), this.text()) : [];
  });
  protected readonly weekdays = computed(() => weekdayNames(this.text().lang).map((d) => d.long.slice(0, 3)));
  protected readonly monthDays = computed(() => monthDays(this.date(), this.data()?.days ?? null, this.text()));
  protected readonly peekHeading = computed(() => {
    const peek = this.store.peek();
    return peek ? longDay(peek.date, this.text().lang) : '';
  });

  constructor() {
    const media = (query: string, target: typeof this.desktop) => {
      const list = this.view?.matchMedia?.(query);
      const onChange = (event: MediaQueryListEvent) => target.set(event.matches);
      list?.addEventListener?.('change', onChange);
      return () => list?.removeEventListener?.('change', onChange);
    };
    const stops = [media(DESKTOP_QUERY, this.desktop), media(NARROW_QUERY, this.narrow)];
    // Esc e picar fóra pechan a vista rápida (primeiro) e a tarxeta; desprazar pecha a vista rápida.
    const onKey = (event: KeyboardEvent) => {
      if (event.key !== 'Escape') return;
      if (this.store.peek()) this.closePeek(true);
      // Tamén co foco no bloque ou no detalle; non se o Esc vén dunha capa (diálogo, menú).
      else if (!(event.target as Element | null)?.closest?.('.cdk-overlay-container')) this.closeDetail();
    };
    const onDown = (event: MouseEvent) => {
      const target = event.target as Element | null;
      if (this.store.peek() && !target?.closest?.('[data-peek], [data-peek-trigger]')) this.closePeek(false);
      if (this.outsideDetail(event)) this.closeDetail();
    };
    const onScroll = () => this.closePeek(false);
    this.document.addEventListener('keydown', onKey);
    this.document.addEventListener('mousedown', onDown);
    this.document.addEventListener('scroll', onScroll, { capture: true, passive: true });
    inject(DestroyRef).onDestroy(() => {
      stops.forEach((stop) => stop());
      this.document.removeEventListener('keydown', onKey);
      this.document.removeEventListener('mousedown', onDown);
      this.document.removeEventListener('scroll', onScroll, { capture: true });
    });
    // Despois de cobrar ou cancelar desde a tarxeta, o período vólvese pedir (sen esqueleto).
    effect(() => {
      if (this.manage.updated()) untracked(() => this.reload(true));
    });
  }

  ngOnInit(): void {
    if (!this.slug()) return;
    void this.manage.loadFacilities(this.slug());
    this.reload();
  }

  /** Vai a outra vista ou data (frechas, calendario en capa, «Ver o día»). */
  protected go(mode: CalendarMode | null, date = this.date()): void {
    this.closePeek(false);
    this.mode.set(mode ?? this.mode());
    this.date.set(date);
    this.reload();
  }

  /** Unha instalación no día pasa á semana; «Todas» vale en todas as vistas. */
  protected setFacility(id: string): void {
    this.facilityId.set(id);
    this.go(id && this.mode() === 'DAY' ? 'WEEK' : null);
  }

  /** Abre a vista rápida dese día (ou hora); premer de novo no mesmo botón péchaa. */
  protected openPeek(request: PeekRequest): void {
    if (this.store.peek() && this.peekAnchor() === request.trigger) {
      this.closePeek(true);
      return;
    }
    this.peekAnchor.set(request.trigger);
    void this.store.openPeek(this.slug(), request.date, request.hour, this.facilityId() || null);
    afterNextRender(() => this.document.querySelector<HTMLElement>('[data-peek]')?.focus({ preventScroll: true }), {
      injector: this.injector,
    });
  }

  protected closePeek(returnFocus: boolean): void {
    if (!this.store.peek()) return;
    const anchor = this.peekAnchor();
    this.store.closePeek();
    this.peekAnchor.set(null);
    if (returnFocus) anchor?.focus();
  }

  /** Picar nunha reserva (bloque, fila ou vista rápida): ábrea ao lado ou nun diálogo; de novo, péchaa. */
  protected select(booking: ManagedBookingResponse): void {
    this.closePeek(false);
    if (this.selectedId() === booking.id) {
      this.closeDetail();
      return;
    }
    this.selectedRow.set(booking);
    void this.manage.open(this.slug(), booking.id);
    if (this.desktop()) return;
    const data: BookingDetailDialogData = { facility: this.selectedFacility() };
    const ref = this.dialogs.open(BookingDetailDialogComponent, { data, injector: this.injector });
    ref.closed.subscribe(() => this.resetDetail());
  }

  protected closeDetail(): void {
    if (this.manage.detail() === null) return;
    slideOut(this.view, this.detailCard()?.nativeElement, () => this.resetDetail());
  }

  protected reload(quiet = false): void {
    const query = { mode: this.mode(), date: this.date(), facilityId: this.facilityId() || null };
    if (this.slug()) void this.store.load(this.slug(), query, quiet);
  }

  private resetDetail(): void {
    this.manage.close();
    this.selectedRow.set(null);
  }

  /** O detalle está ao lado e o evento vén de fóra del, dos bloques e de calquera capa. */

  private outsideDetail(event: Event): boolean {
    if (!this.desktop() || this.manage.detail() === null) return false;
    const target = event.target as Element | null;
    return !target?.closest?.('[data-detail-card], [data-cal-block], [data-peek], .cdk-overlay-container');
  }
}
